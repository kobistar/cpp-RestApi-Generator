cmake_minimum_required(VERSION 3.2)

project(cpp-poco-petstore)
set(CMAKE_VERBOSE_MAKEFILE ON)
set(CMAKE_INCLUDE_CURRENT_DIR ON)
set(CMAKE_AUTOMOC ON)

set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -fPIC -Wall -Wno-unused-variable")

 find_package(Poco REQUIRED Foundation Net NetSSL JSON)

include_directories(
    ${CMAKE_CURRENT_SOURCE_DIR}/client
)

add_subdirectory(client)
add_executable(${PROJECT_NAME}
    PetStore/main.cpp
    )

target_link_libraries(${PROJECT_NAME} PRIVATE poco_client)
target_link_libraries(${PROJECT_NAME} PRIVATE Poco::Foundation Poco::JSON Poco::Net)
set_property(TARGET ${PROJECT_NAME} PROPERTY CXX_STANDARD 14)
set_property(TARGET ${PROJECT_NAME} PROPERTY CXX_STANDARD_REQUIRED ON)
set_property(TARGET ${PROJECT_NAME} PROPERTY CXX_EXTENSIONS OFF)

install(TARGETS ${PROJECT_NAME} RUNTIME DESTINATION bin LIBRARY DESTINATION lib ARCHIVE DESTINATION lib)
