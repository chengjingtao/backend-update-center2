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
//
//import hudson.util.VersionNumber;
//import org.apache.commons.io.FileUtils;
//import org.apache.commons.io.IOUtils;
//import org.apache.lucene.index.IndexReader;
//import org.apache.lucene.search.BooleanClause.Occur;
//import org.apache.lucene.search.BooleanQuery;
//import org.apache.lucene.search.IndexSearcher;
//import org.apache.lucene.store.FSDirectory;
//import org.apache.maven.artifact.Artifact;
//import org.apache.maven.artifact.factory.ArtifactFactory;
//import org.apache.maven.artifact.repository.ArtifactRepository;
//import org.apache.maven.artifact.repository.ArtifactRepositoryFactory;
//import org.apache.maven.artifact.repository.ArtifactRepositoryPolicy;
//import org.apache.maven.artifact.repository.layout.DefaultRepositoryLayout;
//import org.apache.maven.artifact.resolver.AbstractArtifactResolutionException;
//import org.apache.maven.artifact.resolver.ArtifactResolver;
//import org.apache.maven.artifact.transform.ArtifactTransformationManager;
//import org.apache.maven.index.expr.SourcedSearchExpression;
//import org.apache.maven.wagon.Wagon;
//import org.apache.tools.ant.taskdefs.Expand;
//import org.codehaus.plexus.*;
//import org.codehaus.plexus.classworlds.ClassWorld;
//import org.codehaus.plexus.component.repository.ComponentDescriptor;
//import org.codehaus.plexus.component.repository.exception.ComponentLookupException;
//import org.apache.maven.index.creator.*;
//import org.apache.maven.index.*;
//import org.apache.maven.index.context.*;
//
//import java.io.*;
//import java.net.URL;
//import java.net.URLConnection;
//import java.nio.file.Paths;
//import java.util.ArrayList;
//import java.util.Collection;
//import java.util.List;
//import java.util.Map;
//import java.util.Properties;
//import java.util.TreeMap;
//import org.apache.maven.index.updater.*;
//import org.apache.maven.index.updater.WagonHelper;
//
///**
// * Maven repository and its nexus index.
// *
// * Using Maven embedder 2.0.4 results in problem caused by Plexus incompatibility.
// *
// * @author Kohsuke Kawaguchi
// */
//public class MavenRepositoryImpl extends MavenRepository {
//    protected Indexer indexer;
//
//    protected ArtifactFactory af;
//    protected ArtifactResolver ar;
//    protected List<ArtifactRepository> remoteRepositories = new ArrayList<ArtifactRepository>();
//    protected ArtifactRepository local;
//    protected ArtifactRepositoryFactory arf;
//    private PlexusContainer plexus;
//    private boolean offlineIndex;
//    private boolean directLink;
//    private IndexingContext indexingContext;
//    private final Wagon httpWagon;
//
//    public MavenRepositoryImpl() throws Exception {
//        this(false);
//    }
//
//    public MavenRepositoryImpl(boolean directLink) throws Exception {
//        this.directLink = directLink;
////        ClassWorld classWorld = new ClassWorld( "plexus.core", MavenRepositoryImpl.class.getClassLoader() );
////        ContainerConfiguration configuration = new DefaultContainerConfiguration().setClassWorld( classWorld );
////
//////        configuration.setClassPathScanning( PlexusConstants.SCANNING_INDEX );
////
////        plexus = new DefaultPlexusContainer( configuration );
//
//        final DefaultContainerConfiguration config = new DefaultContainerConfiguration();
//        config.setClassPathScanning( PlexusConstants.SCANNING_INDEX );
//        this.plexus = new DefaultPlexusContainer( config );
//
//        ComponentDescriptor<ArtifactTransformationManager> componentDescriptor = plexus.getComponentDescriptor(ArtifactTransformationManager.class,
//            ArtifactTransformationManager.class.getName(), "default");
//
//        if (componentDescriptor == null) {
//            throw new IllegalArgumentException("Unable to find maven default ArtifactTransformationManager component. You might get this if you run the program from within the exec:java mojo.");
//        }
//        componentDescriptor.setImplementationClass(DefaultArtifactTransformationManager.class);
//
//        System.out.println("===>");
//
//        indexer = plexus.lookup( Indexer.class );
//        System.out.println("===>2");
//
//        af = plexus.lookup(ArtifactFactory.class);
//        System.out.println("===>3");
//
//        ar = plexus.lookup(ArtifactResolver.class);
//        arf = plexus.lookup(ArtifactRepositoryFactory.class);
//        // lookup wagon used to remotely fetch index
//        this.httpWagon = plexus.lookup( Wagon.class, "http" );
//
//        System.out.println("===>2");
//        local = arf.createArtifactRepository("local",
//                new File(new File(System.getProperty("user.home")), ".m2/repository").toURI().toURL().toExternalForm(),
//                new DefaultRepositoryLayout(), POLICY, POLICY);
//        System.out.println("out maven repo");
//    }
//
//    /**
//     * Set to true to force reusing locally cached index and not download new versions.
//     * Useful for debugging.
//     */
//    public void setOfflineIndex(boolean offline) {
//        this.offlineIndex = offline;
//    }
//
//    /**
//     * Plexus container that's hosting the Maven components.
//     */
//    public PlexusContainer getPlexus() {
//        return plexus;
//    }
//
//    /**
//     * @param id
//     *      Repository ID. This ID has to match the ID in the repository index, due to a design bug in Maven.
//     * @param indexDirectory
//     *      Directory that contains exploded index zip file.
//     * @param repository
//     *      URL of the Maven repository. Used to resolve artifacts.
//     */
//    public void addRemoteRepository(String id, File indexDirectory, URL repository) throws IOException, UnsupportedExistingLuceneIndexException {
//        List<IndexCreator> indexCreatorList = new ArrayList<IndexCreator>();
//        indexCreatorList.add(new MinimalArtifactInfoIndexCreator());
//        indexCreatorList.add(new JarFileContentsIndexCreator());
//        indexCreatorList.add(new HpiFixupIndexCreator());
//
//        System.out.println("repository index creator...");
//        indexCreatorList.add(new RepositoryIndexCreator(repository));
//        System.out.println("adding index context");
//
//        indexingContext =
//                indexer.createIndexingContext( id, id, null, indexDirectory,
//                        repository.toString(), null, true, true, indexCreatorList );
//
//
//        System.out.println("added index context");
//        remoteRepositories.add(
//                arf.createArtifactRepository(id, repository.toExternalForm(),
//                        new DefaultRepositoryLayout(), POLICY, POLICY));
//    }
//
//    public void addRemoteRepository(String id, URL repository) throws IOException, UnsupportedExistingLuceneIndexException {
//        addRemoteRepository(id,new URL(repository,".index/nexus-maven-repository-index.gz"), repository);
//    }
//
//    public void addRemoteRepository(String id, URL remoteIndex, URL repository) throws IOException, UnsupportedExistingLuceneIndexException {
//        addRemoteRepository(id,loadIndex(id,remoteIndex), repository);
//    }
//
//    /**
//     * Loads a remote repository index (.zip or .gz), convert it to Lucene index and return it.
//     */
//    private File loadIndex(String id, URL url) throws IOException, UnsupportedExistingLuceneIndexException {
//        File dir = new File(new File(System.getProperty("java.io.tmpdir")), "maven-index/" + id);
//        File local = new File(dir,"index"+getExtension(url));
//        File expanded = new File(dir,"expanded");
//
//        ResourceFetcher resourceFetcher = new WagonHelper.WagonFetcher( httpWagon, null, null, null );
//        InputStream gzInputStream = null;
//
//
//        if (!expanded.exists() || !local.exists() ||  true) {
//            System.out.println("Downloading "+url);
//            // if the download fail in the middle, only leave a broken tmp file
//            dir.mkdirs();
//            File tmp = new File(dir,"index_"+ "gz");
//            FileOutputStream o = null;
//            try{
//                o = new FileOutputStream(tmp);
//                resourceFetcher.connect(id, "http://118.24.199.49:32008/repository/maven-releases");
//                gzInputStream = resourceFetcher.retrieve(".index/nexus-maven-repository-index.gz");
//                IOUtils.copy(gzInputStream, o);
//            }finally {
//                o.close();
//                gzInputStream.close();
//            }
//
//            if (expanded.exists())
//                FileUtils.deleteDirectory(expanded);
//            expanded.mkdirs();
//
//            if (url.toExternalForm().endsWith(".gz") || true) {
//                System.out.println("Reconstructing index from "+url);
//                FSDirectory directory = FSDirectory.open(Paths.get(System.getProperty("java.io.tmpdir") , "/maven-index/" + id));
//
//                NexusAnalyzer analyzer = null;
//                NexusIndexWriter w = null;
//                FileInputStream in = null;
//
//                try {
//                    analyzer = new NexusAnalyzer();
//                    w = new NexusIndexWriter(directory, analyzer, true);
//                    in = new FileInputStream(tmp);
//                    System.out.println("===>begin init index data reader");
//                    IndexDataReader dr = new IndexDataReader(in);
//                    System.out.println("===> inited");
//                    List<IndexCreator> indexCreatorList = new ArrayList<IndexCreator>();
//                    indexCreatorList.add(new MinimalArtifactInfoIndexCreator());
//                    indexCreatorList.add(new JarFileContentsIndexCreator());
//
//                    IndexDataReader.IndexDataReadResult result = dr.readIndex(w,
//                            new DefaultIndexingContext(id,id,null,expanded,null,null,indexCreatorList,true));
//                    System.out.println("===> readed index, got result");
//                } finally {
//                    analyzer.close();
//                    IndexUtils.close(w);
//                    IOUtils.closeQuietly(in);
//                    directory.close();
//                }
//            } else
//            if (url.toExternalForm().endsWith(".zip")) {
//                Expand e = new Expand();
//                e.setSrc(tmp);
//                e.setDest(expanded);
//                e.execute();
//            } else {
//                throw new UnsupportedOperationException("Unsupported index format: "+url);
//            }
//
//            // as a proof that the expansion was properly completed
//            tmp.renameTo(local);
////            local.setLastModified(con.getLastModified());
//        } else {
//            System.out.println("Reusing the locally cached "+url+" at "+local);
//        }
//        System.out.println("get expanded "+url );
//        return expanded;
//    }
//
//
//    /**
//     * Loads a remote repository index (.zip or .gz), convert it to Lucene index and return it.
//     */
//    private File loadIndex2(String id, URL url) throws IOException, UnsupportedExistingLuceneIndexException {
//        File dir = new File(new File(System.getProperty("java.io.tmpdir")), "maven-index/" + id);
//        File local = new File(dir,"index"+getExtension(url));
//        File expanded = new File(dir,"expanded");
//
//        ResourceFetcher resourceFetcher = new WagonHelper.WagonFetcher( httpWagon, null, null, null );
//        resourceFetcher.connect(id, "http://118.24.199.49:32008/repository/maven-releases");
//        resourceFetcher.retrieve(".index/nexus-maven-repository-index.gz");
//
//
//        URLConnection con = url.openConnection();
//        if (url.getUserInfo()!=null) {
//            con.setRequestProperty("Authorization","Basic "+new sun.misc.BASE64Encoder().encode(url.getUserInfo().getBytes()));
//        }
//
//        if (!expanded.exists() || !local.exists() || (local.lastModified()!=con.getLastModified() && !offlineIndex)) {
//            System.out.println("Downloading "+url);
//            // if the download fail in the middle, only leave a broken tmp file
//            dir.mkdirs();
//            File tmp = new File(dir,"index_"+getExtension(url));
//            FileOutputStream o = null;
//            try{
//                o = new FileOutputStream(tmp);
//                IOUtils.copy(con.getInputStream(), o);
//            }finally {
//                o.close();
//            }
//
//            if (expanded.exists())
//                FileUtils.deleteDirectory(expanded);
//            expanded.mkdirs();
//
//            if (url.toExternalForm().endsWith(".gz")) {
//                System.out.println("Reconstructing index from "+url);
//                FSDirectory directory = FSDirectory.open(Paths.get(System.getProperty("java.io.tmpdir") , "/maven-index/" + id));
//
//                NexusAnalyzer analyzer = null;
//                NexusIndexWriter w = null;
//                FileInputStream in = null;
//
//                try {
//                    analyzer = new NexusAnalyzer();
//                    w = new NexusIndexWriter(directory, analyzer, true);
//                    in = new FileInputStream(tmp);
//                    System.out.println("===>begin init index data reader");
//                    IndexDataReader dr = new IndexDataReader(in);
//                    System.out.println("===> inited");
//                    List<IndexCreator> indexCreatorList = new ArrayList<IndexCreator>();
//                    indexCreatorList.add(new MinimalArtifactInfoIndexCreator());
//                    indexCreatorList.add(new JarFileContentsIndexCreator());
//
//                    IndexDataReader.IndexDataReadResult result = dr.readIndex(w,
//                            new DefaultIndexingContext(id,id,null,expanded,null,null,indexCreatorList,true));
//                    System.out.println("===> readed index, got result");
//                } finally {
//                    analyzer.close();
//                    IndexUtils.close(w);
//                    IOUtils.closeQuietly(in);
//                    directory.close();
//                }
//            } else
//            if (url.toExternalForm().endsWith(".zip")) {
//                Expand e = new Expand();
//                e.setSrc(tmp);
//                e.setDest(expanded);
//                e.execute();
//            } else {
//                throw new UnsupportedOperationException("Unsupported index format: "+url);
//            }
//
//            // as a proof that the expansion was properly completed
//            tmp.renameTo(local);
//            local.setLastModified(con.getLastModified());
//        } else {
//            System.out.println("Reusing the locally cached "+url+" at "+local);
//        }
//        System.out.println("get expanded "+url );
//        return expanded;
//    }
//
//    private static String getExtension(URL url) {
//        String s = url.toExternalForm();
//        int idx = s.lastIndexOf('.');
//        if (idx<0)  return "";
//        else        return s.substring(idx);
//    }
//
//    protected File resolve(ArtifactInfo a, String type, String classifier) throws AbstractArtifactResolutionException {
//        Artifact artifact = af.createArtifactWithClassifier(a.getGroupId(), a.getArtifactId(), a.getVersion(), type, classifier);
//        ar.resolve(artifact, remoteRepositories, local);
//        return artifact.getFile();
//    }
//
//    public Collection<PluginHistory> listHudsonPlugins() throws PlexusContainerException, ComponentLookupException, IOException, UnsupportedExistingLuceneIndexException, AbstractArtifactResolutionException {
//        System.out.println("begin list hudson plugins...");
//
//        BooleanQuery q = new BooleanQuery();
//        q.add(indexer.constructQuery(MAVEN.PACKAGING,new SourcedSearchExpression( "hpi" )), Occur.MUST);
//
//        FlatSearchRequest request = new FlatSearchRequest(q);
//        FlatSearchResponse response = indexer.searchFlat(request);
//
//        Map<String, PluginHistory> plugins =
//            new TreeMap<String, PluginHistory>(String.CASE_INSENSITIVE_ORDER);
//
//        for (ArtifactInfo a : response.getResults()) {
//            if (a.getVersion().contains("SNAPSHOT"))     continue;       // ignore snapshots
//
//            if (IGNORE.containsKey(a.getArtifactId()) || IGNORE.containsKey(a.getArtifactId() + "-" + a.getVersion()))
//                continue;       // artifactIds or particular versions to omit
//
//            PluginHistory p = plugins.get(a.getArtifactId());
//            if (p==null)
//                plugins.put(a.getArtifactId(), p=new PluginHistory(a.getArtifactId()));
//            p.addArtifact(createHpiArtifact(a, p));
//            p.groupId.add(a.getGroupId());
//        }
//        return plugins.values();
//    }
//
//    public TreeMap<VersionNumber,HudsonWar> getHudsonWar() throws IOException, AbstractArtifactResolutionException {
//        TreeMap<VersionNumber,HudsonWar> r = new TreeMap<VersionNumber, HudsonWar>(VersionNumber.DESCENDING);
//        listWar(r, "org.jenkins-ci.main", null);
//        listWar(r, "org.jvnet.hudson.main", CUT_OFF);
//        return r;
//    }
//
//    private void listWar(TreeMap<VersionNumber, HudsonWar> r, String groupId, VersionNumber cap) throws IOException {
//        BooleanQuery q = new BooleanQuery();
//        q.add(indexer.constructQuery(MAVEN.GROUP_ID,new SourcedSearchExpression( groupId ) ), Occur.MUST);
//        q.add(indexer.constructQuery(MAVEN.PACKAGING,new SourcedSearchExpression("war")), Occur.MUST);
//
//        FlatSearchRequest request = new FlatSearchRequest(q);
//        FlatSearchResponse response = indexer.searchFlat(request);
//
//        for (ArtifactInfo a : response.getResults()) {
//            if (a.getVersion().contains("SNAPSHOT"))     continue;       // ignore snapshots
//            if (!a.getArtifactId().equals("jenkins-war")
//             && !a.getArtifactId().equals("hudson-war"))  continue;      // somehow using this as a query results in 0 hits.
//            if (a.getClassifier()!=null)  continue;          // just pick up the main war
//            if (cap!=null && new VersionNumber(a.getVersion()).compareTo(cap)>0) continue;
//
//            VersionNumber v = new VersionNumber(a.getVersion());
//            r.put(v, createHudsonWarArtifact(a));
//        }
//    }
//
///*
//    Hook for subtypes to use customized implementations.
// */
//
//    protected HPI createHpiArtifact(ArtifactInfo a, PluginHistory p) throws AbstractArtifactResolutionException {
//        return directLink?new DirectHPI(this, p, a):new HPI(this,p,a);
//    }
//
//    protected HudsonWar createHudsonWarArtifact(ArtifactInfo a) {
//        return new HudsonWar(this,a);
//    }
//
//    private static final Properties IGNORE = new Properties();
//
//    static {
//        try {
//            IGNORE.load(Plugin.class.getClassLoader().getResourceAsStream("artifact-ignores.properties"));
//        } catch (IOException e) {
//            throw new Error(e);
//        }
//    }
//
//    protected static final ArtifactRepositoryPolicy POLICY = new ArtifactRepositoryPolicy(true, "daily", "warn");
//
//    /**
//     * Hudson -> Jenkins cut-over version.
//     */
//    public static final VersionNumber CUT_OFF = new VersionNumber("1.395");
//}
