package dev.jeka.core.tool;

import dev.jeka.core.api.utils.JkUtilsObject;
import dev.jeka.core.api.utils.JkUtilsReflect;
import dev.jeka.core.api.utils.JkUtilsString;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.stream.Collectors;

/*
 * Defines Jeka classes defined on a given project.
 *
 * @author Jerome Angibaud
 */
final class ProjectDef {

    /**
     * Defines methods and options available on a given build class.
     *
     * @author Jerome Angibaud
     */
    static final class RunClassDef {

        private final List<RunMethodDef> methodDefs;

        private final List<JkClassOptionDef> optionDefs;

        private final Object runOrPlugin;

        private RunClassDef(Object runOrPlugin, List<RunMethodDef> methodDefs,
                            List<JkClassOptionDef> optionDefs) {
            super();
            this.runOrPlugin = runOrPlugin;
            this.methodDefs = Collections.unmodifiableList(methodDefs);
            this.optionDefs = Collections.unmodifiableList(optionDefs);
        }

        List<RunMethodDef> methodDefinitions() {
            return methodDefs;
        }

        List<JkClassOptionDef> optionDefs() {
            return optionDefs;
        }

        static RunClassDef of(Object run) {
            final Class<?> clazz = run.getClass();
            final List<RunMethodDef> methods = new LinkedList<>();
            for (final Method method : executableMethods(clazz)) {
                methods.add(RunMethodDef.of(method));
            }
            Collections.sort(methods);
            final List<JkClassOptionDef> options = new LinkedList<>();
            for (final NameAndField nameAndField : options(clazz, "", true, null)) {
                options.add(JkClassOptionDef.of(run, nameAndField.field,
                        nameAndField.name, nameAndField.rootClass));
            }
            Collections.sort(options);
            return new RunClassDef(run, methods, options);
        }

        private static List<Method> executableMethods(Class<?> clazz) {
            final List<Method> result = new LinkedList<>();
            for (final Method method : clazz.getMethods()) {
                final int modifier = method.getModifiers();
                if (method.getReturnType().equals(void.class) && method.getParameterTypes().length == 0
                        && !JkUtilsReflect.isMethodPublicIn(Object.class, method.getName())
                        && !Modifier.isAbstract(modifier) && !Modifier.isStatic(modifier)) {
                    result.add(method);
                }

            }
            return result;
        }

        private static List<NameAndField> options(Class<?> clazz, String prefix, boolean root, Class<?> rClass) {
            final List<NameAndField> result = new LinkedList<>();
            for (final Field field : FieldInjector.getOptionFields(clazz)) {
                final Class<?> rootClass = root ? field.getDeclaringClass() : rClass;
                if (!hasSubOption(field)) {
                    result.add(new NameAndField(prefix + field.getName(), field, rootClass));
                } else {
                    final List<NameAndField> subOpts = options(field.getType(), prefix + field.getName() + ".", false,
                            rootClass);
                    result.addAll(subOpts);
                }
            }
            return result;
        }

        private static boolean hasSubOption(Field field) {
            return !JkUtilsReflect.getAllDeclaredFields(field.getType(), JkDoc.class).isEmpty();
        }

        String description() {
            StringBuilder stringBuilder = new StringBuilder();
            for (Class<? extends JkClass> buildClass : this.runClassHierarchy()) {
                stringBuilder.append(description(buildClass, "", true, false));
            }
            return stringBuilder.toString();
        }

        String flatDescription(String prefix) {
            return description(this.runOrPlugin.getClass(), prefix, false, true);
        }



        private String description(Class<?> runClass, String prefix, boolean withHeader, boolean includeHierarchy) {
            List<RunMethodDef> methods = includeHierarchy ? this.methodDefs : this.methodsOf(runClass);
            List<JkClassOptionDef> options = includeHierarchy ? this.optionDefs : this.optionsOf(runClass);
            if (methods.isEmpty() && options.isEmpty()) {
                return "";
            }
            String classWord = JkClass.class.isAssignableFrom(runClass) ? "class" : "plugin";
            StringBuilder stringBuilder = new StringBuilder();
            if (withHeader) {
                stringBuilder.append("\nFrom " + classWord + " " + runClass.getName() + " :\n");
            }
            String margin = withHeader ? "  " : "";
            if (!methods.isEmpty()) {
                stringBuilder.append(margin + "Methods :\n");
                for (RunMethodDef methodDef : methods) {
                    final String displayedMethodName = prefix + methodDef.name;
                    if (methodDef.description == null) {
                        stringBuilder.append( margin + "  " + displayedMethodName + " : No description available.\n");
                    } else {
                        stringBuilder.append(margin).append("  ").append(displayedMethodName)
                                .append(" : ").append(methodDef.description.replace("\n", " "))
                                .append("\n");
                    }
                }
            }
            if (!options.isEmpty()) {
                stringBuilder.append(margin + "Options :\n");
                for (JkClassOptionDef optionDef : options) {
                    stringBuilder.append(optionDef.description(prefix, margin));
                }
            }
            return stringBuilder.toString();
        }

        Map<String, String> optionValues(JkClass run) {
            final Map<String, String> result = new LinkedHashMap<>();
            for (final JkClassOptionDef optionDef : this.optionDefs) {
                final String name = optionDef.name;
                final Object value = JkClassOptionDef.value(run, name);
                result.put(name, JkUtilsObject.toString(value));
            }
            return result;
        }

        Element toElement(Document document) {
            final Element runEl = document.createElement("run");
            final Element methodsEl = document.createElement("methods");
            runEl.appendChild(methodsEl);
            for (final RunMethodDef runMethodDef : this.methodDefs) {
                final Element methodEl = runMethodDef.toXmlElement(document);
                methodsEl.appendChild(methodEl);
            }
            final Element optionsEl = document.createElement("options");
            runEl.appendChild(optionsEl);
            for (final JkClassOptionDef jkClassOptionDef : this.optionDefs) {
                final Element optionEl = jkClassOptionDef.toElement(document);
                optionsEl.appendChild(optionEl);
            }
            return runEl;
        }

        private List<Class<? extends JkClass>> runClassHierarchy() {
            List<Class<? extends JkClass>> result = new ArrayList<>();
            Class<?> current = this.runOrPlugin.getClass();
            while (JkClass.class.isAssignableFrom(current) || JkPlugin.class.isAssignableFrom(current)) {
                result.add((Class<? extends JkClass>) current);
                current = current.getSuperclass();
            }
            return result;
        }

        private List<RunMethodDef> methodsOf(Class<?> runClass) {
            return this.methodDefs.stream().filter(runMethodDef -> runClass.equals(runMethodDef.declaringClass))
                    .collect(Collectors.toList());
        }

        private List<JkClassOptionDef> optionsOf(Class<?> runClass) {
            return this.optionDefs.stream().filter(jkClassOptionDef -> runClass.equals(jkClassOptionDef.rootDeclaringClass))
                    .collect(Collectors.toList());
        }
    }

    /**
     * Definition of method in a given class that can be called by Jeka.
     *
     * @author Jerome Angibaud
     */
     static final class RunMethodDef implements Comparable<RunMethodDef> {

        private final String name;

        private final String description;

        private final Class<?> declaringClass;

        private RunMethodDef(String name, String description, Class<?> declaringClass) {
            super();
            this.name = name;
            this.description = description;
            this.declaringClass = declaringClass;
        }

        static RunMethodDef of(Method method) {
            final JkDoc jkDoc = JkUtilsReflect.getInheritedAnnotation(method, JkDoc.class);
            final String descr = jkDoc != null ? JkUtilsString.join(jkDoc.value(), "\n") : null;
            return new RunMethodDef(method.getName(), descr, method.getDeclaringClass());
        }

        @Override
        public int compareTo(RunMethodDef other) {
            if (this.declaringClass.equals(other.declaringClass)) {
                return this.name.compareTo(other.name);
            }
            if (this.declaringClass.isAssignableFrom(other.declaringClass)) {
                return -1;
            }
            return 1;
        }

        Element toXmlElement(Document document) {
            final Element methodEl = document.createElement("method");
            final Element nameEl = document.createElement("name");
            nameEl.setTextContent(this.name);
            final Element descriptionEl = document.createElement("description");
            if (description != null) {
                descriptionEl.appendChild(document.createCDATASection(description));
            }
            final Element classEl = document.createElement("declaringClass");
            classEl.setTextContent(declaringClass.getName());
            methodEl.appendChild(nameEl);
            methodEl.appendChild(descriptionEl);
            methodEl.appendChild(classEl);
            return methodEl;
        }

    }

    /**
     * Definition for Jeka class option. Jeka class options are fields belonging to a
     * Jeka class.
     *
     * @author Jerome Angibaud
     */
     static final class JkClassOptionDef implements Comparable<JkClassOptionDef> {

        private final String name;

        private final String description;

        private final Object run;

        private final Object defaultValue;

        private final Class<?> rootDeclaringClass;

        private final Class<?> type;

        private final String envVarName;

        private JkClassOptionDef(String name, String description, Object jkRun, Object defaultValue,
                                 Class<?> type, Class<?> declaringClass, String envVarName) {
            super();
            this.name = name;
            this.description = description;
            this.run = jkRun;
            this.defaultValue = defaultValue;
            this.type = type;
            this.rootDeclaringClass = declaringClass;
            this.envVarName = envVarName;
        }

        static JkClassOptionDef of(Object instance, Field field, String name, Class<?> rootDeclaringClass) {
            final JkDoc opt = field.getAnnotation(JkDoc.class);
            final String descr = opt != null ? JkUtilsString.join(opt.value(), "\n") : null;
            final Class<?> type = field.getType();
            final Object defaultValue = value(instance, name);
            final JkEnv env = field.getAnnotation(JkEnv.class);
            final String envVarName = env != null ? env.value() : null;
            return new JkClassOptionDef(name, descr, instance, defaultValue, type, rootDeclaringClass, envVarName);
        }

        private static Object value(Object runInstance, String optName) {
            if (!optName.contains(".")) {
                return JkUtilsReflect.getFieldValue(runInstance, optName);
            }
            final String first = JkUtilsString.substringBeforeFirst(optName, ".");
            Object firstObject = JkUtilsReflect.getFieldValue(runInstance, first);
            if (firstObject == null) {
                final Class<?> firstClass = JkUtilsReflect.getField(runInstance.getClass(), first).getType();
                firstObject = JkUtilsReflect.newInstance(firstClass);
            }
            final String last = JkUtilsString.substringAfterFirst(optName, ".");
            return value(firstObject, last);
        }

        @Override
        public int compareTo(JkClassOptionDef other) {
            if (this.run.getClass().equals(other.run.getClass())) {
                return this.name.compareTo(other.name);
            }
            if (this.run.getClass().isAssignableFrom(other.run.getClass())) {
                return -1;
            }
            return 1;
        }

        String shortDescription() {
            return name + " = " + defaultValue;
        }

        String description(String prefix, String margin) {
            String desc = description != null ? description : "No description available.";
            String oneLineDesc = desc.replace("\n", " ");
            String envPart = envVarName == null ? "" : ", env : " + envVarName;
            return String.format("%s -%s%s (%s, default : %s%s) : %s\n",
                    margin, prefix, name, type(), defaultValue, envPart, oneLineDesc);
        }

        private String type() {
            if (type.isEnum()) {
                return "Enum of " + enumValues(type);
            }
            return this.type.getSimpleName();
        }

        private static String enumValues(Class<?> enumClass) {
            final Object[] values = enumClass.getEnumConstants();
            final StringBuilder result = new StringBuilder();
            for (int i = 0; i < values.length; i++) {
                result.append(values[i].toString());
                if (i + 1 < values.length) {
                    result.append(", ");
                }
            }
            return result.toString();
        }

        String defaultValue() {
            return this.defaultValue == null ? "null" : this.defaultValue.toString();
        }

        Element toElement(Document document) {
            final Element optionEl = document.createElement("option");
            final Element nameEl = document.createElement("name");
            nameEl.setTextContent(this.name);
            final Element descriptionEl = document.createElement("description");
            if (description != null) {
                descriptionEl.appendChild(document.createCDATASection(this.description));
            }
            final Element typeEl = document.createElement("type");
            typeEl.setTextContent(this.type());
            final Element defaultValueEl = document.createElement("defaultValue");
            defaultValueEl.appendChild(document.createCDATASection(this.defaultValue()));
            optionEl.appendChild(nameEl);
            optionEl.appendChild(descriptionEl);
            optionEl.appendChild(typeEl);
            optionEl.appendChild(defaultValueEl);
            return optionEl;
        }

    }

    private static class NameAndField {
        final String name;
        final Field field;
        final Class<?> rootClass; // for nested fields, we need the class declaring

        // the asScopedDependency object

        NameAndField(String name, Field field, Class<?> rootClass) {
            super();
            this.name = name;
            this.field = field;
            this.rootClass = rootClass;
        }

        @Override
        public String toString() {
            return name + ", to " + rootClass.getName();
        }

    }

}
