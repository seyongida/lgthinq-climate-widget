package dev.thinqclimate.widget;

import org.junit.Test;
import static org.junit.Assert.*;

public class BuildVariantTest {
    @Test public void publicReleaseNeverEmbedsCredentials() {
        if(!BuildConfig.DEBUG) {
            assertTrue("public PAT must be empty",BuildConfig.THINQ_PAT.isEmpty());
            assertEquals("dev.thinqclimate.widget.release",BuildConfig.APPLICATION_ID);
        } else assertEquals("dev.thinqclimate.widget",BuildConfig.APPLICATION_ID);
    }
}
