package me.aymanisam.hungergames;

import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.mockbukkit.mockbukkit.MockBukkit;

/** Supplies the server registries required by the Minecraft 1.21.4 API. */
public final class BukkitTestEnvironment implements BeforeEachCallback, AfterEachCallback {
    @Override public void beforeEach(ExtensionContext context) {
        MockBukkit.mock();
    }

    @Override public void afterEach(ExtensionContext context) {
        MockBukkit.unmock();
    }
}
