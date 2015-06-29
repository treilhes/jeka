package org.jerkar.api.depmanagement;

import org.jerkar.api.depmanagement.JkDependency;
import org.jerkar.api.depmanagement.JkExternalModule;
import org.jerkar.api.depmanagement.JkModuleId;
import org.jerkar.api.depmanagement.JkVersionRange;
import org.junit.Test;

public class JkExternalModuleTest {

	@SuppressWarnings("unused")
	@Test
	public void testOf() {
		JkDependency dep;
		dep = JkExternalModule.of(JkModuleId.of("org.hibernate", "hibernate-core"), JkVersionRange.of("3.0.1.Final"));
		dep = JkExternalModule.of("org.hibernate", "hibernate-core", "3.0.1.Final");
		dep = JkExternalModule.of("org.hibernate:hibernate-core:3.0.1+");
	}



}