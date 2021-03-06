package dev.jeka.core.api.depmanagement;

import dev.jeka.core.api.utils.JkUtilsIterable;

import java.util.*;
import java.util.stream.Collectors;

/**
 * A mapping to scopes to scopes acting when declaring dependencies. The goal of a scope mapping is to determine :<ul>
 * <li>which scopes a dependency is declared for</li>
 * <li>for each scope a dependency is declared, which scopes of its transitive dependencies to retrieve</li>
 * </ul>.
 *
 * For example, Your component 'A' depends of component 'B' for compiling. You can declare 'A' depends of 'B' with scope 'compile'. <br/>
 * Now imagine that for compiling, 'A' needs also the test class of 'B' along the dependencies 'B' needs for testing. For such, you
 * can declare a scope mapping as 'compile->compile, test'.
 *
 * This concept matches strictly with the <i>configuration</i> concept found in Ivy : <a href="http://wrongnotes.blogspot.be/2014/02/simplest-explanation-of-ivy.html">see here.</a>.
 */
public final class JkScopeMapping {

    /**
     * Useful when using scope mapping. As documented in Ivy, it stands for the main archive.
     */
    public static final String ARCHIVE_MASTER = "archives(master)";

    /**
     * Scope mapping used by default.
     */
    public static final JkScopeMapping DEFAULT_SCOPE_MAPPING = JkScopeMapping
            .of(JkScope.COMPILE).to(ARCHIVE_MASTER, JkScope.COMPILE.getName() + "(default)")
            .and(JkScope.PROVIDED).to(ARCHIVE_MASTER, JkScope.COMPILE.getName() + "(default)")
            .and(JkScope.RUNTIME).to(ARCHIVE_MASTER, JkScope.RUNTIME.getName() + "(default)")
            .and(JkScope.TEST).to(ARCHIVE_MASTER, JkScope.RUNTIME.getName() + "(default)");
    private final Map<JkScope, Set<String>> map;

    // -------- Factory methods ----------------------------

    /**
     * Returns a partially constructed mapping specifying only scope entries and
     * willing for the mapping values.
     */
    public static JkScopeMapping.Partial of(JkScope scope, JkScope... others) {
        return of(JkUtilsIterable.listOf1orMore(scope, others));
    }

    /**
     * Returns a partially constructed mapping specifying only scope entries and
     * willing for the mapping values.
     */
    public static JkScopeMapping.Partial of(String scope, String... others) {
        return of(JkUtilsIterable.listOf1orMore(scope, others).stream().map(JkScope::of).collect(Collectors.toList()));
    }

    /**
     * Returns a partially constructed mapping specifying only scope entries and
     * willing for the mapping values.
     */
    @SuppressWarnings("unchecked")
    public static JkScopeMapping.Partial of(Iterable<JkScope> scopes) {
        return new Partial(scopes, new JkScopeMapping(Collections.EMPTY_MAP));
    }

    /**
     * Creates an empty scope mapping.
     */
    @SuppressWarnings("unchecked")
    public static JkScopeMapping of() {
        return new JkScopeMapping(Collections.EMPTY_MAP);
    }

    // ---------------- Instance members ---------------------------



    private JkScopeMapping(Map<JkScope, Set<String>> map) {
        super();
        this.map = map;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + map.hashCode();
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final JkScopeMapping other = (JkScopeMapping) obj;
        return (map.equals(other.map));
    }

    public JkScopeMapping minus(JkScope scope) {
        final Map<JkScope, Set<String>> newMap = new HashMap<>(this.map);
        newMap.remove(scope);
        return new JkScopeMapping(newMap);
    }

    /**
     * Returns a partial object to construct a scope mapping identical to this one but augmented with the specified
     * mapping. The specified arguments stands for the left side scopes of the mapping to be construct.
     */
    public Partial and(JkScope... from) {
        return and(Arrays.asList(from));
    }

    /**
     * @see #and(JkScope...)
     */
    public Partial and(Iterable<JkScope> from) {
        return new Partial(from, this);
    }

    private JkScopeMapping andFromTo(JkScope from, Iterable<String> to) {
        final Map<JkScope, Set<String>> result = new HashMap<>(map);
        if (result.containsKey(from)) {
            final Set<String> list = result.get(from);
            final Set<String> newList = new HashSet<>(list);
            newList.addAll(JkUtilsIterable.listOf(to));
            result.put(from, Collections.unmodifiableSet(newList));
        } else {
            final Set<String> newList = new HashSet<>();
            newList.addAll(JkUtilsIterable.listOf(to));
            result.put(from, Collections.unmodifiableSet(newList));
        }
        return new JkScopeMapping(result);
    }

    /**
     * Returns the right side scope mapped to the specified left scope.
     */
    public Set<String> getMappedScopes(JkScope sourceScope) {
        final Set<String> result = this.map.get(sourceScope);
        if (result != null && !result.isEmpty()) {
            return result;
        }
        throw new IllegalArgumentException("No mapped scope declared for " + sourceScope
                + ". Declared scopes are " + this.getEntries());
    }

    /**
     * Returns all the scopes declared on the left side of this scope mapping.
     */
    public Set<JkScope> getEntries() {
        return Collections.unmodifiableSet(this.map.keySet());
    }


    @Override
    public String toString() {
        return map.toString();
    }

    /**
     * Partial object to construct a scope mapping. The partial object contains only the left side
     * of a scope mapping entries.
     */
    public static class Partial {

        private final Iterable<JkScope> from;

        private final JkScopeMapping mapping;

        private Partial(Iterable<JkScope> from, JkScopeMapping mapping) {
            super();
            this.from = from;
            this.mapping = mapping;
        }


        /**
         * Similar to {@link #to(String...)} but allow raw string as parameter
         */
        public JkScopeMapping to(String... targets) {
            final List<String> list = new LinkedList<>();
            for (final String target : targets) {
                list.add(target);
            }
            return to(list);
        }


        /**
         * @see #to(String...)
         */
        public JkScopeMapping to(Iterable<String> targets) {
            JkScopeMapping result = mapping;
            for (final JkScope fromScope : from) {
                for (final String toScope : targets) {
                    result = result.andFromTo(fromScope, JkUtilsIterable.setOf(toScope));
                }
            }
            return result;
        }

    }

}