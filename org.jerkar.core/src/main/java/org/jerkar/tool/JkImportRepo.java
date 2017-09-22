package org.jerkar.tool;

import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.TYPE)
@Repeatable(JkImportRepo.ImportRepos.class)
@Retention(RetentionPolicy.SOURCE)
public @interface JkImportRepo {

    String value();

    @Target(ElementType.TYPE)
    @interface ImportRepos {
        JkImportRepo[] value();
    }
}