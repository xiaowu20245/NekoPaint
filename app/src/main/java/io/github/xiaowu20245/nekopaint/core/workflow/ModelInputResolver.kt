package io.github.xiaowu20245.nekopaint.core.workflow

/**
 * 工作流参数 → ComfyUI 模型目录。
 *
 * 只映射已知的加载器；未知自定义节点保持文本输入，不猜测目录。
 */
object ModelInputResolver {

    fun folderFor(classType: String, inputName: String): String? = when (classType) {
        "CheckpointLoaderSimple" -> if (inputName == "ckpt_name") "checkpoints" else null
        "UNETLoader" -> if (inputName == "unet_name") "diffusion_models" else null
        "CLIPLoader" -> if (inputName == "clip_name") "text_encoders" else null
        "VAELoader" -> if (inputName == "vae_name") "vae" else null
        "LoraLoader", "LoraLoaderModelOnly" -> if (inputName == "lora_name") "loras" else null
        "ControlNetLoader" -> if (inputName == "control_net_name") "controlnet" else null
        "UpscaleModelLoader" -> if (inputName == "model_name") "upscale_models" else null
        "StyleModelLoader" -> if (inputName == "style_model_name") "style_models" else null
        "CLIPVisionLoader" -> if (inputName == "clip_name") "clip_vision" else null
        "HypernetworkLoader" -> if (inputName == "hypernetwork_name") "hypernetworks" else null
        "GLIGENLoader" -> if (inputName == "gligen_name") "gligen" else null
        "IPAdapterModelLoader" -> if (inputName == "ipadapter_file") "ipadapter" else null
        else -> null
    }
}
