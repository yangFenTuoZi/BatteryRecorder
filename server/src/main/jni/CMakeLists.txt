cmake_minimum_required(VERSION 3.22.1)
project("batteryrecorder")

add_library(batteryrecorder SHARED
        power_reader.c
)

find_library(log-lib log)

target_link_libraries(batteryrecorder
        ${log-lib}
)

set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -Wall -Wextra")
