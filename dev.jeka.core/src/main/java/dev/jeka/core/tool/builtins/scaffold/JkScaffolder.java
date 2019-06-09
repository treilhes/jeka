package dev.jeka.core.tool.builtins.scaffold;

import dev.jeka.core.tool.JkConstants;
import dev.jeka.core.api.file.JkPathTree;
import dev.jeka.core.api.function.JkRunnables;
import dev.jeka.core.api.system.JkLocator;
import dev.jeka.core.api.system.JkLog;

import dev.jeka.core.api.utils.JkUtilsIO;
import dev.jeka.core.api.utils.JkUtilsPath;

import java.nio.charset.Charset;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Object that process scaffolding.
 */
public final class JkScaffolder {

    private final JkPathTree baseTree;

    private String buildClassCode;

    private boolean embed;

    public final JkRunnables extraActions = JkRunnables.noOp();

    JkScaffolder(Path baseDir, boolean embed) {
        super();
        this.baseTree = JkPathTree.of(baseDir);
        this.buildClassCode = "";
        this.embed = embed;
    }

    public void setEmbbed(boolean embed) {
        this.embed = embed;
    }

    /**
     * Runs the scaffolding.
     */
    public void run() {
        final Path def = baseTree.getRoot().resolve(JkConstants.DEF_DIR);
        JkUtilsPath.createDirectories(def);
        JkLog.info("Create " + def);
        final Path buildClass = def.resolve("Build.java");
        JkLog.info("Create " + buildClass);
        JkUtilsPath.write(buildClass, buildClassCode.getBytes(Charset.forName("UTF-8")));
        if (embed) {
            JkLog.info("Create shell files.");
            JkUtilsIO.copyUrlToFile(JkScaffolder.class.getClassLoader().getResource("META-INF/bin/jeka.bat"), baseTree.getRoot().resolve("jeka.bat"));
            JkUtilsIO.copyUrlToFile(JkScaffolder.class.getClassLoader().getResource("META-INF/bin/jeka"), baseTree.getRoot().resolve("jeka"));
            Path jekaJar = JkLocator.getJekaJarPath();
            Path bootFolder = baseTree.getRoot().resolve("build/boot");
            JkUtilsPath.createDirectories(bootFolder);
            Path target = bootFolder.resolve(jekaJar.getFileName());
            JkLog.info("Copy jeka jar to " + baseTree.getRoot().relativize(target));
            JkUtilsPath.copy(jekaJar, target, StandardCopyOption.REPLACE_EXISTING);
        }
        extraActions.run();
    }

    public void setRunClassCode(String code) {
        this.buildClassCode = code;
    }



}
