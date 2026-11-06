package io.github.xiaowu20245.nekopaint.core.workflow

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ModelInputResolverTest {

    @Test
    fun mapsCommonLoaderInputs() {
        assertEquals("checkpoints", ModelInputResolver.folderFor("CheckpointLoaderSimple", "ckpt_name"))
        assertEquals("loras", ModelInputResolver.folderFor("LoraLoader", "lora_name"))
        assertEquals("vae", ModelInputResolver.folderFor("VAELoader", "vae_name"))
        assertEquals("diffusion_models", ModelInputResolver.folderFor("UNETLoader", "unet_name"))
        assertEquals("text_encoders", ModelInputResolver.folderFor("CLIPLoader", "clip_name"))
        assertEquals("controlnet", ModelInputResolver.folderFor("ControlNetLoader", "control_net_name"))
    }

    @Test
    fun ignoresUnknownInputsAndNodes() {
        assertNull(ModelInputResolver.folderFor("CheckpointLoaderSimple", "config_name"))
        assertNull(ModelInputResolver.folderFor("MysteryLoader", "model_name"))
    }
}
