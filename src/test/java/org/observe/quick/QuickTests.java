package org.observe.quick;

import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.junit.runners.Suite.SuiteClasses;
import org.observe.expresso.ExpressoTests;
import org.observe.quick.style.QuickStyleTests;

/** Runs all unit tests in the ObServe project. */
@RunWith(Suite.class)
@SuiteClasses({ //
	ExpressoTests.class, //
	QuickStyleTests.class
})
public class QuickTests {
}
