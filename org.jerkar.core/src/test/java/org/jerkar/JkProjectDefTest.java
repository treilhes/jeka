package org.jerkar;

import org.jerkar.JkProjectDef.JkProjectBuildClassDef;
import org.jerkar.builtins.javabuild.JkJavaBuild;
import org.junit.Test;

public class JkProjectDefTest {

	@Test
	public void testCreationAndLog() {
		final JkProjectBuildClassDef def = JkProjectBuildClassDef.of(MyBuild.class);
		final boolean silent = JkOptions.isSilent();
		JkOptions.forceSilent(true);
		def.log(true);
		JkOptions.forceSilent(silent);
	}


	static class MyBuild extends JkJavaBuild {

		@JkOption("This is toto")
		private boolean toto;

		@JkOption("PGP")
		private MyClass myClass;

		@Override
		@JkDoc("mydoc")
		public void doDefault() {
			super.doDefault();
		}

	}

	static class MyClass {

		@JkOption("This is my value")
		private String myValue;

		@JkOption("my class number 2")
		private MyClass2 myClass2;
	}

	static class MyClass2 {

		@JkOption("my value 2")
		public boolean myValue2;
	}

}