# Compile-only SeeU Extra Continuation API signatures

This isolated Gradle source set is used only for compiling World Bosses without a local SeeU Extra JAR. It mirrors the API entry points used by World Bosses from SeeU Extra Continuation 0.3.0. **These are NOT functional implementations and must never be included in the runtime classpath or packaged JAR.**

If the SeeU Extra API changes, update these signatures in lockstep with its published version. World Bosses still requires the real `seeu_extra` mod at runtime (see `neoforge.mods.toml`).
