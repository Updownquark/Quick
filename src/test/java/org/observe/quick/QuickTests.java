package org.observe.quick;

import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.junit.runners.Suite.SuiteClasses;
import org.observe.quick.style.QuickStyleTests;

/** Runs all unit tests in the ObServe project. */
@RunWith(Suite.class)
@SuiteClasses({ //
	QuickStyleTests.class
})
public class QuickTests {
}
