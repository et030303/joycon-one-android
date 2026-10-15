cmake_minimum_required(VERSION 3.22.1)
project(uhid_relay C)

# Executables disguised as shared libraries for APK packaging.
# Android's build system only packages lib*.so from native builds.
foreach(helper uhid_relay evdev_reader)
    add_executable(${helper} ${helper}.c)
    set_target_properties(${helper} PROPERTIES
        OUTPUT_NAME "lib${helper}"
        SUFFIX ".so"
        RUNTIME_OUTPUT_DIRECTORY "${CMAKE_LIBRARY_OUTPUT_DIRECTORY}"
    )
endforeach()
