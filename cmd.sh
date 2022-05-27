#!/bin/bash
source /etc/profile
#first set your Android Path here
AndroidPlatformPath=$ANDROID_HOME/platforms
targetBenchmark=BenchmarkAndResults
echo $ANDROID_HOME
#clean previous execution results
rm -rf VALA-output-skip1
mkdir VALA-output-skip1
rm -rf VALA-output-skip2
mkdir VALA-output-skip2
rm -rf VALA-output-swap
mkdir VALA-output-swap

java -jar bin/VALA.jar -lpr skip1 -a $targetBenchmark/skip1/bugapps -o VALA-output-skip1 -p $AndroidPlatformPath -s bin/ResourceOPMap.xml -mm bin/MappingMethods.xml -eu bin/errorusage.xml
java -jar bin/VALA.jar -lpr skip2 -a $targetBenchmark/skip2/bugapps -o VALA-output-skip2 -p $AndroidPlatformPath -s bin/ResourceOPMap.xml -mm bin/MappingMethods.xml -eu bin/errorusage.xml
java -jar bin/VALA.jar -lpr swap -tsf -a $targetBenchmark/swap/bugapps -o VALA-output-swap -p $AndroidPlatformPath -s bin/ResourceOPMap.xml -mm bin/MappingMethods.xml -eu bin/errorusage.xml

