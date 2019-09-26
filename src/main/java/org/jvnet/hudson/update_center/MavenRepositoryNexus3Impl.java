/*
 * The MIT License
 *
 * Copyright (c) 2004-2009, Sun Microsystems, Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.jvnet.hudson.update_center;

import hudson.util.VersionNumber;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.factory.ArtifactFactory;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.repository.ArtifactRepositoryFactory;
import org.apache.maven.artifact.repository.ArtifactRepositoryPolicy;
import org.apache.maven.artifact.repository.layout.DefaultRepositoryLayout;
import org.apache.maven.artifact.resolver.AbstractArtifactResolutionException;
import org.apache.maven.artifact.resolver.ArtifactResolver;
import org.apache.maven.index.*;
import org.apache.maven.index.context.*;
import org.apache.maven.index.creator.JarFileContentsIndexCreator;
import org.apache.maven.index.creator.MinimalArtifactInfoIndexCreator;
import org.apache.maven.index.expr.SourcedSearchExpression;
import org.apache.maven.index.updater.*;
import org.apache.maven.wagon.Wagon;
import org.apache.maven.wagon.events.TransferEvent;
import org.apache.maven.wagon.events.TransferListener;
import org.apache.maven.wagon.observers.AbstractTransferListener;
import org.codehaus.plexus.*;
import org.codehaus.plexus.component.repository.exception.ComponentLookupException;

import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;
import java.util.logging.Logger;

/**
 * Maven repository and its nexus index.
 * <p>
 * Using Maven embedder 2.0.4 results in problem caused by Plexus incompatibility.
 *
 * @author Kohsuke Kawaguchi
 */
public class MavenRepositoryNexus3Impl extends MavenRepository {

    private static final Logger LOGGER = Logger.getLogger(MavenRepositoryNexus3Impl.class.getName());

    private final PlexusContainer plexusContainer;
    private final Indexer indexer;
    private final IndexUpdater indexUpdater;
    private final Wagon httpWagon;
    private IndexingContext centralContext;

    private boolean directLink;
    private boolean offlineIndex;


    protected ArtifactFactory af;
    protected ArtifactResolver ar;
    protected List<ArtifactRepository> remoteRepositories = new ArrayList<ArtifactRepository>();
    protected ArtifactRepository local;
    protected ArtifactRepositoryFactory arf;

    public MavenRepositoryNexus3Impl(boolean directLink) throws PlexusContainerException, ComponentLookupException, MalformedURLException {

        this.directLink = directLink;

        final DefaultContainerConfiguration config = new DefaultContainerConfiguration();
        config.setClassPathScanning(PlexusConstants.SCANNING_INDEX);
        this.plexusContainer = new DefaultPlexusContainer(config);

        this.indexer = plexusContainer.lookup(Indexer.class);
        this.indexUpdater = plexusContainer.lookup(IndexUpdater.class);
        this.httpWagon = plexusContainer.lookup(Wagon.class, "http");

        af = plexusContainer.lookup(ArtifactFactory.class);
        ar = plexusContainer.lookup(ArtifactResolver.class);
        arf = plexusContainer.lookup(ArtifactRepositoryFactory.class);
        local = arf.createArtifactRepository("local",
                new File(new File(System.getProperty("user.home")), ".m2/repository").toURI().toURL().toExternalForm(),
                new DefaultRepositoryLayout(), POLICY, POLICY);
    }


    /**
     * Set to true to force reusing locally cached index and not download new versions.
     * Useful for debugging.
     */
    public void setOfflineIndex(boolean offline) {
        this.offlineIndex = offline;
    }


    public Collection<PluginHistory> listHudsonPlugins() throws PlexusContainerException, ComponentLookupException, IOException, UnsupportedExistingLuceneIndexException, AbstractArtifactResolutionException {

        System.out.println("List jenkins plugins...");
        System.out.println();

        BooleanQuery q = new BooleanQuery();
        q.add(indexer.constructQuery(MAVEN.PACKAGING, new SourcedSearchExpression("hpi")), Occur.MUST);

        FlatSearchRequest request = new FlatSearchRequest(q, centralContext);
        FlatSearchResponse response = indexer.searchFlat(request);


        Map<String, PluginHistory> plugins = new TreeMap<String, PluginHistory>(String.CASE_INSENSITIVE_ORDER);
        for (ArtifactInfo a : response.getResults()){
            if (a.getVersion().contains("SNAPSHOT")){
                continue;
            }

            if (IGNORE.containsKey(a.getArtifactId()) || IGNORE.containsKey(a.getArtifactId() + "-" + a.getVersion()))
                continue;       // artifactIds or particular versions to omit

            PluginHistory p = plugins.get(a.getArtifactId());
            if (p == null) {
                p = new PluginHistory(a.getArtifactId());
                plugins.put(a.getArtifactId(), p);
            }
            System.out.println("  ->: " + a.getGroupId() +"/" + a.getArtifactId() + "-" + a.getVersion() + "." + a.getFileExtension());

            p.addArtifact(createHpiArtifact(a, p));
            p.groupId.add(a.getGroupId());
        }
        System.out.println();
        return plugins.values();
    }

    private void initIndexingContext(String id, String repositoryId, String repositoryUrl) throws IOException {
        LOGGER.info("Initing indexing context...");

        // Files where local cache is (if any) and Lucene Index should be located
        File centralLocalCache = new File("target/central-cache");
        File centralIndexDir = new File("target/central-index");
        List<IndexCreator> indexCreators = new ArrayList<IndexCreator>();
        indexCreators.add(new MinimalArtifactInfoIndexCreator());
        indexCreators.add(new JarFileContentsIndexCreator());
        indexCreators.add(new HpiFixupIndexCreator());

        centralContext = indexer.createIndexingContext(id, repositoryId, centralLocalCache, centralIndexDir,
                repositoryUrl, null, true, true, indexCreators);
    }

    private void updateIndex() throws IOException {
        System.out.println("Updating Index...");

        TransferListener listener = new AbstractTransferListener() {
            @Override
            public void transferStarted(TransferEvent transferEvent) {
                super.transferStarted(transferEvent);
                System.out.print(" Downloading " + transferEvent.getResource().getName());
            }

            @Override
            public void transferProgress(TransferEvent transferEvent, byte[] buffer, int length) {
                super.transferProgress(transferEvent, buffer, length);
            }

            @Override
            public void transferCompleted(TransferEvent transferEvent) {
                super.transferCompleted(transferEvent);
                System.out.println(" - Done ");
            }
        };

        ResourceFetcher resourceFetcher = new WagonHelper.WagonFetcher(httpWagon, listener, null, null);
        Date centralContextCurrentTimestamp = centralContext.getTimestamp();
        IndexUpdateRequest updateRequest = new IndexUpdateRequest(centralContext, resourceFetcher);
        IndexUpdateResult updateResult = indexUpdater.fetchAndUpdateIndex(updateRequest);

        if (updateResult.isFullUpdate()) {
            System.out.println("Full update happened !");
        } else if (updateResult.getTimestamp() == null || updateResult.getTimestamp().equals(centralContextCurrentTimestamp)) {
            System.out.println("No update needed, index is up to date !");

        } else {
            System.out.println(
                    "Incremental update happened, change covered " + centralContextCurrentTimestamp + " - "
                            + updateResult.getTimestamp() + " period.");
        }
        System.out.println("Updated index");
    }

    public void close()throws IOException {
        indexer.closeIndexingContext( centralContext, false );
    }


    /**
     * @param repositoryName Repository ID. This ID has to match the ID in the repository index, due to a design bug in Maven.
     * @param repositoryUrl     URL of the Maven repository. Used to resolve artifacts.
     */
    public void addRemoteRepository(String repositoryName, String repositoryUrl) throws IOException {

        initIndexingContext(repositoryName, repositoryName, repositoryUrl);
        updateIndex();

        ArtifactRepository artifactRepository = arf.createArtifactRepository(repositoryName, repositoryUrl,new DefaultRepositoryLayout(), POLICY, POLICY);
        remoteRepositories.add( artifactRepository );

        this.setRepositoryURL(repositoryUrl);
    }


    private static String getExtension(URL url) {
        String s = url.toExternalForm();
        int idx = s.lastIndexOf('.');
        if (idx < 0) return "";
        else return s.substring(idx);
    }

    protected File resolve(ArtifactInfo a, String type, String classifier) throws AbstractArtifactResolutionException {
        Artifact artifact = af.createArtifactWithClassifier(a.getGroupId(), a.getArtifactId(), a.getVersion(), type, classifier);
        ar.resolve(artifact, remoteRepositories, local);
        return artifact.getFile();
    }


    public TreeMap<VersionNumber, HudsonWar> getHudsonWar() throws IOException, AbstractArtifactResolutionException {
        TreeMap<VersionNumber, HudsonWar> r = new TreeMap<VersionNumber, HudsonWar>(VersionNumber.DESCENDING);
        listWar(r, "org.jenkins-ci.main", null);
        listWar(r, "org.jvnet.hudson.main", CUT_OFF);
        return r;
    }

    private void listWar(TreeMap<VersionNumber, HudsonWar> r, String groupId, VersionNumber cap) throws IOException {
        BooleanQuery q = new BooleanQuery();
        q.add(indexer.constructQuery(MAVEN.GROUP_ID, new SourcedSearchExpression(groupId)), Occur.MUST);
        q.add(indexer.constructQuery(MAVEN.PACKAGING, new SourcedSearchExpression("war")), Occur.MUST);

        FlatSearchRequest request = new FlatSearchRequest(q, centralContext);
        FlatSearchResponse response = indexer.searchFlat(request);

        for (ArtifactInfo a : response.getResults()) {
            if (a.getVersion().contains("SNAPSHOT")) continue;       // ignore snapshots
            if (!a.getArtifactId().equals("jenkins-war")
                    && !a.getArtifactId().equals("hudson-war"))
                continue;      // somehow using this as a query results in 0 hits.
            if (a.getClassifier() != null) continue;          // just pick up the main war
            if (cap != null && new VersionNumber(a.getVersion()).compareTo(cap) > 0) continue;

            VersionNumber v = new VersionNumber(a.getVersion());
            r.put(v, createHudsonWarArtifact(a));
        }
    }

/*
    Hook for subtypes to use customized implementations.
 */

    protected HPI createHpiArtifact(ArtifactInfo a, PluginHistory p) throws AbstractArtifactResolutionException {
        return directLink ? new DirectHPI(this, p, a) : new HPI(this, p, a);
    }

    protected HudsonWar createHudsonWarArtifact(ArtifactInfo a) {
        return new HudsonWar(this, a);
    }

    private static final Properties IGNORE = new Properties();

    static {
        try {
            IGNORE.load(Plugin.class.getClassLoader().getResourceAsStream("artifact-ignores.properties"));
        } catch (IOException e) {
            throw new Error(e);
        }
    }

    protected static final ArtifactRepositoryPolicy POLICY = new ArtifactRepositoryPolicy(true, "daily", "warn");

    /**
     * Hudson -> Jenkins cut-over version.
     */
    public static final VersionNumber CUT_OFF = new VersionNumber("1.395");
}
