/*
 * Copyright (C) 2007 The Android Open Source Project
 *
 * Licensed under the Eclipse Public License, Version 1.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.eclipse.org/org/documents/epl-v10.php
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.ide.eclipse.adt.internal.project;

import com.android.ide.eclipse.adt.internal.project.AndroidManifestHelper;
import com.android.ide.eclipse.tests.AdtTestData;
import com.android.sdklib.xml.ManifestData;

import junit.framework.TestCase;

/**
 * Tests for {@link AndroidManifestHelper}
 */
public class AndroidManifestParserTest extends TestCase {
    private ManifestData mManifestTestApp;
    private ManifestData mManifestInstrumentation;

    private static final String TESTDATA_PATH =
        "com/android/ide/eclipse/testdata/";  //$NON-NLS-1$
    private static final String INSTRUMENTATION_XML = TESTDATA_PATH +
        "AndroidManifest-instrumentation.xml";  //$NON-NLS-1$
    private static final String TESTAPP_XML = TESTDATA_PATH +
        "AndroidManifest-testapp.xml";  //$NON-NLS-1$
    private static final String PACKAGE_NAME =  "com.android.testapp"; //$NON-NLS-1$
    private static final Integer VERSION_CODE = 42;
    private static final String ACTIVITY_NAME = "com.android.testapp.MainActivity"; //$NON-NLS-1$
    private static final String LIBRARY_NAME = "android.test.runner"; //$NON-NLS-1$
    private static final String INSTRUMENTATION_NAME = "android.test.InstrumentationTestRunner"; //$NON-NLS-1$
    private static final String INSTRUMENTATION_TARGET = "com.android.AndroidProject"; //$NON-NLS-1$

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        String testFilePath = AdtTestData.getInstance().getTestFilePath(TESTAPP_XML);
        mManifestTestApp = AndroidManifestHelper.parseForData(testFilePath);
        assertNotNull(mManifestTestApp);

        testFilePath = AdtTestData.getInstance().getTestFilePath(INSTRUMENTATION_XML);
        mManifestInstrumentation = AndroidManifestHelper.parseForData(testFilePath);
        assertNotNull(mManifestInstrumentation);
    }

    public void testGetInstrumentationInformation() {
        assertEquals(1, mManifestInstrumentation.getInstrumentations().length);
        assertEquals(INSTRUMENTATION_NAME,
                mManifestInstrumentation.getInstrumentations()[0].getName());
        assertEquals(INSTRUMENTATION_TARGET,
                mManifestInstrumentation.getInstrumentations()[0].getTargetPackage());
    }

    public void testGetPackage() {
        assertEquals(PACKAGE_NAME, mManifestTestApp.getPackage());
    }

    public void testGetVersionCode() {
        assertEquals(VERSION_CODE, mManifestTestApp.getVersionCode());
        assertEquals(null, mManifestInstrumentation.getVersionCode());
    }

    public void testMinSdkVersion() {
        assertEquals("7", mManifestTestApp.getApiLevelRequirement());
    }

    public void testGetActivities() {
        assertEquals(1, mManifestTestApp.getActivities().length);
        ManifestData.Activity activity = mManifestTestApp.getActivities()[0];
        assertEquals(ACTIVITY_NAME, activity.getName());
        assertTrue(activity.hasAction());
        assertTrue(activity.isHomeActivity());
        assertTrue(activity.hasAction());
        assertEquals(activity, mManifestTestApp.getActivities()[0]);
    }

    public void testGetLauncherActivity() {
        ManifestData.Activity activity = mManifestTestApp.getLauncherActivity();
        assertEquals(ACTIVITY_NAME, activity.getName());
        assertTrue(activity.hasAction());
        assertTrue(activity.isHomeActivity());
    }

    private void assertEquals(ManifestData.Activity lhs, ManifestData.Activity rhs) {
        assertTrue(lhs == rhs || (lhs != null && rhs != null));
        if (lhs != null && rhs != null) {
            assertEquals(lhs.getName(),        rhs.getName());
            assertEquals(lhs.isExported(),     rhs.isExported());
            assertEquals(lhs.hasAction(),      rhs.hasAction());
            assertEquals(lhs.isHomeActivity(), rhs.isHomeActivity());
        }
    }

    public void testGetUsesLibraries() {
        assertEquals(1, mManifestTestApp.getUsesLibraries().length);
        assertEquals(LIBRARY_NAME, mManifestTestApp.getUsesLibraries()[0]);
    }

    public void testGetPackageName() {
        assertEquals(PACKAGE_NAME, mManifestTestApp.getPackage());
    }
}
