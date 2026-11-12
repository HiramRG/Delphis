package io.github.hiramrg.delphis.core

/**
 * "Looks": small post-processing shaders Delphis ships and copies into Dolphin's user Shaders folder,
 * then picks per game with [Video_Enhancements] PostProcessingShader in GameSettings/<id>.ini.
 * They only touch the finished picture (once per frame), so games run at the same speed.
 * Written for Dolphin's post-processing API (works on Vulkan and OpenGL).
 */
object Looks {
    enum class Cost { NONE, LIGHT, MEDIUM }

    enum class Look(val label: String, val description: String, val cost: Cost, val file: String?) {
        ORIGINAL("Original", "What Dolphin shows today.", Cost.NONE, null),
        SHARP("Sharp", "Crisper edges and text.", Cost.LIGHT, "delphis_sharp"),
        VIVID("Vivid", "Richer colour, like a bright TV.", Cost.LIGHT, "delphis_vivid"),
        SCANLINES("Scanlines", "Thin dark lines like an old TV.", Cost.LIGHT, "delphis_scanlines"),
        CRT("CRT", "Scanlines, soft glow and dark corners.", Cost.MEDIUM, "delphis_crt"),
        SOFT("Soft", "Smooths jagged edges.", Cost.LIGHT, "delphis_soft"),
    }

    fun byName(name: String?): Look? = Look.entries.firstOrNull { it.name == name }

    private const val SECTION = "Video_Enhancements"
    private const val KEY = "PostProcessingShader"

    /** Writes the look into a game's settings file; Original removes only a shader Delphis set. */
    fun applyToGameIni(existing: String, look: Look): String = IniEditor(existing).apply {
        val current = get(SECTION, KEY)
        if (look.file != null) set(SECTION, mapOf(KEY to look.file))
        else if (current != null && current.startsWith("delphis_")) remove(SECTION, KEY)
    }.toString()

    /** Shader source for each look (Dolphin post-processing GLSL). */
    fun source(look: Look): String? = when (look) {
        Look.ORIGINAL -> null
        Look.SHARP -> HEADER + """
void main()
{
    float2 px = GetInvResolution();
    float2 uv = GetCoordinates();
    float4 c = Sample();
    float4 n = (SampleLocation(uv + float2(px.x, 0.0)) + SampleLocation(uv - float2(px.x, 0.0)) +
                SampleLocation(uv + float2(0.0, px.y)) + SampleLocation(uv - float2(0.0, px.y))) * 0.25;
    SetOutput(clamp(c + (c - n) * 0.7, 0.0, 1.0));
}
"""
        Look.VIVID -> HEADER + """
void main()
{
    float4 c = Sample();
    float l = dot(c.rgb, float3(0.299, 0.587, 0.114));
    float3 s = mix(float3(l, l, l), c.rgb, 1.35);
    s = (s - 0.5) * 1.06 + 0.5;
    SetOutput(float4(clamp(s, 0.0, 1.0), c.a));
}
"""
        Look.SCANLINES -> HEADER + """
void main()
{
    float4 c = Sample();
    float y = GetCoordinates().y * 480.0;
    float w = 0.78 + 0.22 * cos(y * 6.2831853);
    SetOutput(float4(clamp(c.rgb * w * 1.08, 0.0, 1.0), c.a));
}
"""
        Look.CRT -> HEADER + """
void main()
{
    float2 px = GetInvResolution() * 1.5;
    float2 uv = GetCoordinates();
    float4 c = Sample();
    float4 b = (SampleLocation(uv + float2(px.x, px.y)) + SampleLocation(uv + float2(-px.x, px.y)) +
                SampleLocation(uv + float2(px.x, -px.y)) + SampleLocation(uv + float2(-px.x, -px.y))) * 0.25;
    float3 col = mix(c.rgb, b.rgb, 0.25) + b.rgb * 0.12;
    float y = uv.y * 480.0;
    col *= 0.8 + 0.2 * cos(y * 6.2831853);
    float2 d = uv - 0.5;
    col *= clamp(1.0 - dot(d, d) * 0.9, 0.0, 1.0);
    SetOutput(float4(clamp(col * 1.1, 0.0, 1.0), c.a));
}
"""
        Look.SOFT -> HEADER + """
void main()
{
    float2 px = GetInvResolution();
    float2 uv = GetCoordinates();
    float4 c = Sample() * 0.4;
    c += (SampleLocation(uv + float2(px.x, 0.0)) + SampleLocation(uv - float2(px.x, 0.0)) +
          SampleLocation(uv + float2(0.0, px.y)) + SampleLocation(uv - float2(0.0, px.y))) * 0.1;
    c += (SampleLocation(uv + px) + SampleLocation(uv - px) +
          SampleLocation(uv + float2(px.x, -px.y)) + SampleLocation(uv + float2(-px.x, px.y))) * 0.05;
    SetOutput(c);
}
"""
    }

    private const val HEADER = "// Delphis look. Written by Delphis; safe to delete (Delphis puts it back when needed).\n"
}
