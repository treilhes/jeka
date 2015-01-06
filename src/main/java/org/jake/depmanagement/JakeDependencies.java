package org.jake.depmanagement;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jake.depmanagement.JakeDependency.JakeFilesDependency;
import org.jake.depmanagement.JakeScopedDependency.ScopeType;
import org.jake.utils.JakeUtilsIterable;

/**
 * A set of {@link JakeScopedDependency} generally standing for the entire dependencies of a project/module.
 * 
 * @author Jerome Angibaud.
 */
public class JakeDependencies implements Iterable<JakeScopedDependency>{

	private final List<JakeScopedDependency> dependencies;

	private JakeDependencies(List<JakeScopedDependency> dependencies) {
		super();
		this.dependencies = Collections.unmodifiableList(new LinkedList<JakeScopedDependency>(dependencies));
	}

	/**
	 * Returns <code>true</code> if this object contains no dependency.
	 */
	public boolean isEmpty() {
		return dependencies.isEmpty();
	}

	/**
	 * Returns a clone of this object minus the dependencies on the given {@link JakeModuleId}.
	 */
	public JakeDependencies without(JakeModuleId jakeModuleId) {
		final List<JakeScopedDependency> result = new LinkedList<JakeScopedDependency>(dependencies);
		for (final Iterator<JakeScopedDependency> it = result.iterator(); it.hasNext();) {
			final JakeDependency dependency = it.next().dependency();
			if (dependency instanceof JakeExternalModule) {
				final JakeExternalModule externalModule = (JakeExternalModule) dependency;
				if (externalModule.moduleId().equals(jakeModuleId)) {
					it.remove();
				}
			}
		}
		return new JakeDependencies(result);
	}

	/**
	 * Returns a clone of this object plus the specified {@link JakeScopedDependency}s.
	 */
	public JakeDependencies and(Iterable<JakeScopedDependency> others) {
		if (!others.iterator().hasNext()) {
			return this;
		}
		return JakeDependencies.builder().on(this).on(others).build();
	}

	/**
	 * Returns a clone of this object plus the specified {@link JakeScopedDependency}s.
	 */
	public JakeDependencies and(JakeScopedDependency... others) {
		return and(Arrays.asList(others));
	}

	/**
	 * Returns <code>true</code> if this object contains dependencies whose are {@link JakeExternalModule}.
	 */
	public boolean containsExternalModule() {
		for (final JakeScopedDependency scopedDependency : dependencies) {
			if (scopedDependency.dependency() instanceof JakeExternalModule) {
				return true;
			}
		}
		return false;
	}

	@Override
	public Iterator<JakeScopedDependency> iterator() {
		return dependencies.iterator();
	}

	@Override
	public String toString() {
		return dependencies.toString();
	}

	/**
	 * Returns the set of {@link JakeDependency} involved for the specified {@link JakeScope}.
	 */
	public Set<JakeDependency> dependenciesDeclaredWith(JakeScope scope) {
		final Set<JakeDependency> dependencies = new HashSet<JakeDependency>();
		for (final JakeScopedDependency scopedDependency : this) {
			if (scopedDependency.scopes().contains(scope)) {
				dependencies.add(scopedDependency.dependency());
			}
		}
		return dependencies;
	}

	/**
	 * Returns the {@link JakeScopedDependency} declared for the specified {@link JakeModuleId}.
	 * Returns <code>null</code> if no dependency on this module exists in this object.
	 */
	public JakeScopedDependency get(JakeModuleId moduleId) {
		for (final JakeScopedDependency scopedDependency : this) {
			final JakeDependency dependency = scopedDependency.dependency();
			if (dependency instanceof JakeExternalModule) {
				final JakeExternalModule externalModule = (JakeExternalModule) dependency;
				if (externalModule.moduleId().equals(moduleId)) {
					return scopedDependency;
				}
			}
		}
		return null;
	}

	/**
	 * Returns the set of scopes involved in these dependencies.
	 */
	public Set<JakeScope> moduleScopes() {
		final Set<JakeScope> result = new HashSet<JakeScope>();
		for (final JakeScopedDependency dep : this.dependencies) {
			if (dep.scopeType() == ScopeType.MAPPED) {
				result.addAll(dep.scopeMapping().entries());
			} else if (dep.scopeType() == ScopeType.SIMPLE) {
				result.addAll(dep.scopes());
			}
		}
		return Collections.unmodifiableSet(result);
	}

	/**
	 * Returns <code>true</code> if this object contains dependency on external module whose rely
	 * on dynamic version.
	 * If so, when resolving, dynamic versions are replaced by fixed resolved ones.
	 */
	public boolean hasDynamicVersions() {
		for (final JakeScopedDependency scopedDependency : this) {
			if (scopedDependency.dependency() instanceof JakeExternalModule) {
				final JakeExternalModule externalModule = (JakeExternalModule) scopedDependency.dependency();
				final JakeVersionRange versionRange = externalModule.versionRange();
				if (!versionRange.isFixed()) {
					return true;
				}
			}
		}
		return false;
	}

	/**
	 * Convenient method to resolve using {@link JakeArtifact}s instead of {@link JakeVersionedModule}.
	 * 
	 * @see #resolvedWith(Iterable);
	 */
	public JakeDependencies resolvedWithArtifacts(Iterable<JakeArtifact> artifacts) {
		final List<JakeVersionedModule> list = new LinkedList<JakeVersionedModule>();
		for (final JakeArtifact artifact : artifacts) {
			list.add(artifact.versionedModule());
		}
		return resolvedWith(list);
	}

	/**
	 * Creates a clone of these dependencies replacing the dynamic versions by the static ones specified
	 * in the {@link JakeVersionedModule}s passed as argument. <br/>
	 */
	public JakeDependencies resolvedWith(Iterable<JakeVersionedModule> resolvedModules) {
		JakeDependencies result = this;
		final Map<JakeModuleId, JakeVersion> map = toModuleVersionMap(resolvedModules);
		for (final JakeVersionedModule versionedModule : resolvedModules) {
			final JakeModuleId moduleId = versionedModule.moduleId();
			final JakeScopedDependency scopedDependency = this.get(moduleId);
			if (scopedDependency == null) {
				continue;
			}
			final JakeExternalModule externalModule = (JakeExternalModule) scopedDependency.dependency();
			if (!externalModule.versionRange().isFixed()) {
				final JakeVersion resolvedVersion = map.get(moduleId);
				if (resolvedVersion != null) {
					final JakeExternalModule resolvedModule = externalModule.resolvedTo(resolvedVersion);
					final JakeScopedDependency resolvedScopedDep = scopedDependency.dependency(resolvedModule);
					result = result.without(moduleId).and(resolvedScopedDep);
				}
			}
		}
		return result;
	}

	private static Map<JakeModuleId, JakeVersion> toModuleVersionMap(Iterable<JakeVersionedModule> resolvedModules) {
		final Map<JakeModuleId, JakeVersion> result = new HashMap<JakeModuleId, JakeVersion>();
		for (final JakeVersionedModule versionedModule : resolvedModules) {
			result.put(versionedModule.moduleId(), versionedModule.version());
		}
		return result;
	}


	public static Builder builder() {
		return new Builder(new LinkedList<JakeScopedDependency>());
	}

	public static class Builder {

		protected final LinkedList<JakeScopedDependency> dependencies;

		private Set<JakeScope> defaultScopes;

		private JakeScopeMapping defaultMapping;

		protected Builder(LinkedList<JakeScopedDependency> dependencies) {
			super();
			this.dependencies = dependencies;
		}

		public Builder forScopes(JakeScope ...scopes) {
			if (scopes.length == 0) {
				throw new IllegalArgumentException("You must specify at least one scope.");
			}
			defaultScopes = JakeUtilsIterable.setOf(scopes);
			defaultMapping = null;
			return this;
		}

		public Builder forScopeMapping(JakeScopeMapping scopeMapping) {
			defaultMapping = scopeMapping;
			defaultScopes = null;
			return this;
		}

		public Builder resetDefaultScope() {
			defaultScopes = null;
			defaultMapping = null;
			return this;
		}



		public ScopeableBuilder on(JakeDependency dependency) {
			final JakeScopedDependency scopedDependency;
			if (defaultScopes != null) {
				scopedDependency = JakeScopedDependency.of(dependency, defaultScopes);
			} else if (defaultMapping != null) {
				scopedDependency = JakeScopedDependency.of(dependency, defaultMapping);
			} else {
				scopedDependency = JakeScopedDependency.of(dependency);
			}
			dependencies.add(scopedDependency);
			if (this instanceof ScopeableBuilder) {
				return (ScopeableBuilder) this;
			}
			return new ScopeableBuilder(dependencies);
		}

		public Builder on(JakeScopedDependency dependency) {
			this.dependencies.add(dependency);
			return this;
		}

		public Builder onFiles(Iterable<File> files) {
			if (!files.iterator().hasNext()) {
				return this;
			}
			return on(JakeFilesDependency.of(files));
		}

		public ScopeableBuilder on(JakeModuleId module, JakeVersionRange version) {
			return on(module, version, true);
		}

		public ScopeableBuilder on(JakeModuleId module, JakeVersionRange version, boolean transitive) {
			return on(JakeExternalModule.of(module, version).transitive(transitive));
		}

		public ScopeableBuilder on(String organisation, String name, String version) {
			return on(organisation, name, version, true);
		}

		public ScopeableBuilder on(String organisation, String name, String version, boolean transitive) {
			return on(JakeExternalModule.of(organisation, name, version).transitive(transitive));
		}

		public ScopeableBuilder on(String description) {
			return on(description, true);
		}

		public ScopeableBuilder on(String description, boolean transitive) {
			return on(JakeExternalModule.of(description).transitive(transitive));
		}

		public Builder on(Iterable<JakeScopedDependency> dependencies) {
			if (!dependencies.iterator().hasNext()) {
				return this;
			}
			for (final JakeScopedDependency dependency : dependencies) {
				this.dependencies.add(dependency);
			}
			return this;
		}

		public JakeDependencies build() {
			return new JakeDependencies(dependencies);
		}

		public static class ScopeableBuilder extends Builder {

			private ScopeableBuilder(LinkedList<JakeScopedDependency> dependencies) {
				super(dependencies);
			}

			public Builder scope(JakeScopeMapping scopeMapping) {
				final JakeDependency dependency = dependencies.pollLast().dependency();
				dependencies.add(JakeScopedDependency.of(dependency, scopeMapping));
				return this;
			}

			public Builder scope(JakeScope ... scopes) {
				final JakeDependency dependency = dependencies.pollLast().dependency();
				dependencies.add(JakeScopedDependency.of(dependency, JakeUtilsIterable.setOf(scopes)));
				return this;
			}

			public AfterMapScopeBuilder mapScope(JakeScope ... scopes) {
				return new AfterMapScopeBuilder(dependencies, JakeUtilsIterable.setOf(scopes) );
			}

			public static class AfterMapScopeBuilder  {

				private final LinkedList<JakeScopedDependency> dependencies;

				private final Iterable<JakeScope> from;

				private AfterMapScopeBuilder(LinkedList<JakeScopedDependency> dependencies, Iterable<JakeScope> from) {
					this.dependencies = dependencies;
					this.from = from;
				}

				public AfterToBuilder to(JakeScope... jakeScopes) {
					final JakeScopedDependency dependency = dependencies.pollLast();
					final JakeScopeMapping mapping;
					if (dependency.scopeType() == JakeScopedDependency.ScopeType.UNSET) {
						mapping = JakeScopeMapping.of(from).to(jakeScopes);
					}  else {
						mapping = dependency.scopeMapping().and(from).to(jakeScopes);
					}
					dependencies.add(JakeScopedDependency.of(dependency.dependency(), mapping));
					return new AfterToBuilder(dependencies);
				}

				public AfterToBuilder to(String... scopeNames) {
					final JakeScope[] scopes = new JakeScope[scopeNames.length];
					for (int i = 0; i < scopeNames.length; i++) {
						scopes[i] = JakeScope.of(scopeNames[i]);
					}
					return to(scopes);
				}

			}

			public static class AfterToBuilder extends Builder {

				private AfterToBuilder(
						LinkedList<JakeScopedDependency> dependencies) {
					super(dependencies);
				}

				public AfterMapScopeBuilder and(JakeScope ...scopes) {
					return new AfterMapScopeBuilder(dependencies, Arrays.asList(scopes));
				}

			}

		}

	}

}
