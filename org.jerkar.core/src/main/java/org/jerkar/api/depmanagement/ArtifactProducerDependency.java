package org.jerkar.api.depmanagement;

import org.jerkar.api.file.JkFileSystemLocalizable;
import org.jerkar.api.file.JkPath;

import java.io.File;
import java.util.List;


/*
 * A dependency on a given artifact file id of a given {@link JkArtifactProducer}
 */
class ArtifactProducerDependency extends JkComputedDependency  {

    private final JkArtifactProducer artifactProducer;

    /*
     * Constructs a {@link ArtifactProducerDependency} from an artifact producer and the artifact file id
     * one is interested on.
     */
    ArtifactProducerDependency(JkArtifactProducer artifactProducer,
                               Iterable<JkArtifactFileId> artifactFileIds) {
        super(() -> artifactProducer.makeArtifactFilesIfNecessary(artifactFileIds),
                baseDir(artifactProducer),
                jarAndRuntimeDeps(artifactProducer, artifactFileIds));
        this.artifactProducer = artifactProducer;
    }

    private static List<File> jarAndRuntimeDeps(JkArtifactProducer producer, Iterable<JkArtifactFileId> artifactIds) {
        JkPath result = JkPath.of();
        for (JkArtifactFileId artifactFileId : artifactIds) {
            result = result.and( producer.runtimeDependencies(artifactFileId)
                    .andHead(producer.artifactFile(artifactFileId)));
        }
        return result.withoutDuplicates().entries();
    }

    private static File baseDir(JkArtifactProducer artifactProducer) {
        if (artifactProducer instanceof JkFileSystemLocalizable) {
            return ((JkFileSystemLocalizable) artifactProducer).baseDir();
        }
        return null;
    }

    @Override
    public String toString() {
        return this.artifactProducer.toString();
    }
}