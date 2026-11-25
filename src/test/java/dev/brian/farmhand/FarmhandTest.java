package dev.brian.farmhand;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class FarmhandTest {
	@Test
	void idUsesModNamespace() {
		var id = Farmhand.id("example");
		assertEquals(Farmhand.MOD_ID, id.getNamespace());
		assertEquals("example", id.getPath());
	}

	@Test
	void langKeyFollowsMinecraftConvention() {
		assertEquals("item.farmhand.example", Farmhand.langKey("item", "example"));
	}
}
