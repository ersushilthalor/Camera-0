package com.example.camera.model

/**
 * AI Super Resolution Hardware Inference Backend:
 * - AUTO: Uses GPU delegate when supported, gracefully falls back to CPU on error or OOM.
 * - CPU: Multi-threaded CPU processing (XNNPACK / SIMD).
 * - GPU: Genuine hardware GPU acceleration via OpenCL / OpenGL ES delegate.
 */
enum class SuperResBackend(val label: String, val description: String) {
    AUTO("Auto", "Automatically use GPU when available, fallback to CPU"),
    CPU("CPU", "Multi-threaded CPU with SIMD / XNNPACK"),
    GPU("GPU", "Direct GPU acceleration via OpenCL / OpenGL ES")
}

/**
 * AI Super Resolution Memory Limit Budget:
 * Controls tile buffer allocations and concurrent execution limits to prevent OutOfMemory (OOM).
 */
enum class SuperResMemoryLimit(val label: String, val maxBytes: Long, val description: String) {
    AUTO("Auto", -1L, "Automatically adapts to available RAM and thermal budget"),
    MB512("512MB", 512L * 1024 * 1024, "Strict 512MB memory ceiling; 1-tile concurrency"),
    GB1("1GB", 1024L * 1024 * 1024, "1GB memory limit; standard 1-tile pipeline"),
    GB2("2GB", 2L * 1024 * 1024 * 1024, "2GB memory limit; safe 1-2 tile concurrency"),
    GB3("3GB", 3L * 1024 * 1024 * 1024, "3GB memory limit; high-throughput 2-tile concurrency"),
    GB4("4GB", 4L * 1024 * 1024 * 1024, "4GB memory limit; maximal concurrency for 200MP")
}
