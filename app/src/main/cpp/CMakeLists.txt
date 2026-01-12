cmake_minimum_required(VERSION 3.22.1)

project("lazyshot")

find_library( # Sets the name of the path variable.
              log-lib

              # Specifies the name of the NDK library that
              # you want CMake to locate.
              log )

find_library( android-lib android )
find_library( camera2ndk-lib camera2ndk )
find_library( mediandk-lib mediandk )

add_library( # Sets the name of the library.
             lazyshot

             # Sets the library as a shared library.
             SHARED

             # Provides a relative path to your source file(s).
             NativeCameraEngine.cpp )

target_link_libraries( # Specifies the target library.
                       lazyshot

                       # Links the target library to the log library
                       # included in the NDK.
                       ${log-lib}
                       ${android-lib}
                       ${camera2ndk-lib}
                       ${mediandk-lib})
