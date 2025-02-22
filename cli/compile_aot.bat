if [%VC_VARS_LOADED%] == [] (
    call "C:\Program Files (x86)\Microsoft Visual Studio\2022\BuildTools\VC\Auxiliary\Build\vcvarsall.bat" amd64
    set VC_VARS_LOADED=YES
)

if not exist build\graalvm mkdir build\graalvm

call "D:\Programs\graalvm-jdk-23.0.2+7.1\bin\native-image.cmd" -jar build\libs\InstaTools-%1.jar -o build\graalvm\InstaTools-%1 --enable-url-protocols=https

if exist build\graalvm\InstaTools-%1.exe (
    start build\graalvm\InstaTools-%1.exe
) else (
    echo An executable was not found!
)
