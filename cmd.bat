Rem first set your Android Path here
set AndroidPlatformPath=%ANDROID_HOME%"\platforms"
set targetBenchmark="BenchmarkAndResults"

Rem clean previous execution results
rmdir /s /Q VALA-output-skip1
mkdir VALA-output-skip1
rmdir /s /Q VALA-output-skip2
mkdir VALA-output-skip2
rmdir /s /Q VALA-output-swap
mkdir VALA-output-swap

java -jar bin\VALA.jar -lpr skip1 -a %targetBenchmark%\skip1\bugapps -o VALA-output-skip1 -p %AndroidPlatformPath% -s bin\ResourceOPMap.xml -mm bin\MappingMethods.xml -eu bin\errorusage.xml
java -jar bin\VALA.jar -lpr skip2 -a %targetBenchmark%\skip2\bugapps -o VALA-output-skip2 -p %AndroidPlatformPath% -s bin\ResourceOPMap.xml -mm bin\MappingMethods.xml -eu bin\errorusage.xml
java -jar bin\VALA.jar -lpr swap -tsf -a %targetBenchmark%\swap\bugapps -o VALA-output-swap -p %AndroidPlatformPath% -s bin\ResourceOPMap.xml -mm bin\MappingMethods.xml -eu bin\errorusage.xml

