/*
 * The MIT License
 * 
 * Copyright (c) 2013 IKEDA Yasuyuki
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

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;

import org.apache.lucene.document.Document;
//import org.sonatype.nexus.index.ArtifactContext;
//import org.sonatype.nexus.index.ArtifactInfo;
//import org.sonatype.nexus.index.creator.AbstractIndexCreator;
import org.apache.maven.index.creator.*;
import org.apache.maven.index.*;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StoredField;


/**
 * Fix artifact extension to hpi.
 *
 */
public class HpiFixupIndexCreator extends AbstractIndexCreator
{

    /**
     * Info: packaging, lastModified, size, sourcesExists, javadocExists, signatureExists. Stored, not indexed.
     */
    public static final IndexerField FLD_INFO = new IndexerField( NEXUS.INFO, IndexerFieldVersion.V1, "i",
            "Artifact INFO (not indexed, stored)", Field.Store.YES, Field.Index.NO );

    public static final IndexerField FLD_GROUP_ID_KW = new IndexerField( MAVEN.GROUP_ID, IndexerFieldVersion.V1, "g",
            "Artifact GroupID (as keyword)", Field.Store.NO, Field.Index.NOT_ANALYZED );

    public static final IndexerField FLD_GROUP_ID = new IndexerField( MAVEN.GROUP_ID, IndexerFieldVersion.V3,
            "groupId", "Artifact GroupID (tokenized)", Field.Store.NO, Field.Index.ANALYZED );

    public static final IndexerField FLD_ARTIFACT_ID_KW = new IndexerField( MAVEN.ARTIFACT_ID, IndexerFieldVersion.V1,
            "a", "Artifact ArtifactID (as keyword)", Field.Store.NO, Field.Index.NOT_ANALYZED );

    public static final IndexerField FLD_ARTIFACT_ID = new IndexerField( MAVEN.ARTIFACT_ID, IndexerFieldVersion.V3,
            "artifactId", "Artifact ArtifactID (tokenized)", Field.Store.NO, Field.Index.ANALYZED );

    public static final IndexerField FLD_VERSION_KW = new IndexerField( MAVEN.VERSION, IndexerFieldVersion.V1, "v",
            "Artifact Version (as keyword)", Field.Store.NO, Field.Index.NOT_ANALYZED );

    public static final IndexerField FLD_VERSION = new IndexerField( MAVEN.VERSION, IndexerFieldVersion.V3, "version",
            "Artifact Version (tokenized)", Field.Store.NO, Field.Index.ANALYZED );

    public static final IndexerField FLD_PACKAGING = new IndexerField( MAVEN.PACKAGING, IndexerFieldVersion.V1, "p",
            "Artifact Packaging (as keyword)", Field.Store.NO, Field.Index.NOT_ANALYZED );

    public static final IndexerField FLD_EXTENSION = new IndexerField( MAVEN.EXTENSION, IndexerFieldVersion.V1, "e",
            "Artifact extension (as keyword)", Field.Store.NO, Field.Index.NOT_ANALYZED );

    public static final IndexerField FLD_CLASSIFIER = new IndexerField( MAVEN.CLASSIFIER, IndexerFieldVersion.V1, "l",
            "Artifact classifier (as keyword)", Field.Store.NO, Field.Index.NOT_ANALYZED );

    public static final IndexerField FLD_NAME = new IndexerField( MAVEN.NAME, IndexerFieldVersion.V1, "n",
            "Artifact name (tokenized, stored)", Field.Store.YES, Field.Index.ANALYZED );

    public static final IndexerField FLD_DESCRIPTION = new IndexerField( MAVEN.DESCRIPTION, IndexerFieldVersion.V1,
            "d", "Artifact description (tokenized, stored)", Field.Store.YES, Field.Index.ANALYZED );

    public static final IndexerField FLD_LAST_MODIFIED = new IndexerField( MAVEN.LAST_MODIFIED, IndexerFieldVersion.V1,
            "m", "Artifact last modified (not indexed, stored)", Field.Store.YES, Field.Index.NO );

    public static final IndexerField FLD_SHA1 = new IndexerField( MAVEN.SHA1, IndexerFieldVersion.V1, "1",
            "Artifact SHA1 checksum (as keyword, stored)", Field.Store.YES, Field.Index.NOT_ANALYZED );


    public static final String ID = "hpi";

    public HpiFixupIndexCreator()
    {
        super( ID);
    }

//    @Override
    public void populateArtifactInfo(ArtifactContext artifactContext)
            throws IOException
    {
        // TODO Auto-generated method stub
        
    }

//    @Override
    public void updateDocument(ArtifactInfo artifactInfo, Document document)
    {
        // TODO Auto-generated method stub
        
    }

//    @Override
    public boolean updateArtifactInfo(Document document,
            ArtifactInfo artifactInfo)
    {
        if (!"hpi".equals(artifactInfo.getPackaging())) return false;

        if (Arrays.asList("hpi", "jpi").contains(artifactInfo.getFileExtension())) return false;

        artifactInfo.setFileExtension("hpi");
        return true;
    }

    public Collection<IndexerField> getIndexerFields()
    {
        return Collections.emptyList();
    }
}
