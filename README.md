# VALA

VALA is a static analyser to detect resource utilization bugs in Android applications induced by variant lifecycles. Now VALA support the bug detection in three variant lifecycles, they are：

SKIP1: When method finish() is called, entrypoint onDestroy will be invoked and the activity will be destroyed immediately after onCreate returns, skipping entrypoints like onStop.

SKIP2: When setRetainInstance(true) is called to retain the Fragment instance, entrypoints onDestroy and onCreate will be skipped whereas the other entrypoints will still be executed in the Fragment lifecycle.

SWAP: When the app is running on devices with 9.0 or higher Android OS, entrypoint onSaveInstanceState will be executed after, instead of before entrypoint onStop.

In the two variant lifecycles of type SKIP a few entrypoints will be skipped, in which the releasing operations of resources can be skipped and resource leaks may occur.
For the third variant, i.e., SWAP, the execution order of entrypoints onSaveInstanceState and onStop is reversed, in which case datas designed to be stored in onSaveInstanceState and cleaned in onStop before Android 9.0 may be lost after Android 9.0.


## Installation

#### System Requirements

- Java: 1.8
- Android SDK: API 19 or higher (make sure `adb` and `aapt` commands are available), and set an environment variable "ANDROID_HOME" for the SDK home path
- Linux: Ubuntu 16.04/Windows/CentOS
- maven: 3.6+

## Usage

VALA is implemented based on Flowdroid---one of the most famous static analysis frameworks for Android applications. As Flowdroid, VALA takes executable APK files as its input, and the entry class of VALA is soot.jimple.infoflow.cmd.MainClass.
You can run VALA using the commond "java -jar VALA.jar ..."
[Setting]
The following options can be used to specify:
-  -a:    the APK file (or the folder containing a list of APK files) to analyze
-  -p:    the path to the platforms directory from the Android SDK
-  -lpr:   the variant lifecycle ("SKIP1", "SKIP2" and "SWAP") to detect inadequate resource usages in
-  -s:    the definition file for operations involved in resource leaks and data loss errors
-  -mm:   the definition file for methods that have specific taint rules
-  -eu:   the definition file for error usages in the variant lifecycles
-  -o:    the folder for outputed XML files 
-  -tsf:  whether to taint sub fields and the children of array/list

Or you can just use the bat/bash script which runs our VALA on all the subject apps (take about 10 minutes) and the results are XML files available inside the "VALA-output-skip1", "VALA-output-skip2" and "VALA-output-swap" folders.

[Output]

The output XML files are in the folder specified by '-o', here is an example reporting a resource leak in lifecycle SKIP1:

```
<PatternDatas>
 <PatternData type="SKIP1">
  <entryclasses>
   <class name="org.microg.gms.ui.AskPushPermission">
    <field field="database:org.microg.gms.gcm.GcmDatabase">
     <method method="<org.microg.gms.ui.AskPushPermission: void onStop()>">
      <oplist optype="android.database.sqlite.SQLiteOpenHelper">
       <RELEASE stmt="virtualinvoke $r4.<android.database.sqlite.SQLiteOpenHelper: void close()>()"/>
      </oplist>
     </method>
     <method method="<org.microg.gms.ui.AskPushPermission: void onCreate(android.os.Bundle)>">
      <oplist optype="default">
       <EDIT stmt="$r0.<org.microg.gms.ui.AskPushPermission$2: org.microg.gms.ui.AskPushPermission this$0> = $r1"/>
      </oplist>
     <oplist optype="default">
      <FINISH stmt="virtualinvoke $r0.<android.app.Activity: void finish()>()"/>
     </oplist>
     <oplist optype="android.database.sqlite.SQLiteOpenHelper">
      <REQUIRE stmt="specialinvoke $r0.<android.database.sqlite.SQLiteOpenHelper: void <init>(android.content.Context,java.lang.String,android.database.sqlite.SQLiteDatabase$CursorFactory,int)>($r1, "gcmstatus", null, $i0)"/>
      <EDIT stmt="$r0.<org.microg.gms.ui.AskPushPermission$2: org.microg.gms.ui.AskPushPermission this$0> = $r1"/>
     </oplist>
     <oplist optype="android.database.sqlite.SQLiteOpenHelper">
      <REQUIRE stmt="specialinvoke $r0.<android.database.sqlite.SQLiteOpenHelper: void <init>(android.content.Context,java.lang.String,android.database.sqlite.SQLiteDatabase$CursorFactory,int)>($r1, "gcmstatus", null, $i0)"/>
      <EDIT stmt="$r0.<org.microg.gms.ui.AskPushPermission: org.microg.gms.gcm.GcmDatabase database> = $r3"/>
      <FINISH stmt="virtualinvoke $r0.<android.app.Activity: void finish()>()"/>
     </oplist>
   </method>
   </field>
  </class>
 </entryclasses>
</PatternData>
<ErrorUsages>
  <ErrorUsage violatedPattern="SKIP1" field="<org.microg.gms.ui.AskPushPermission: org.microg.gms.gcm.GcmDatabase database>">
    <entrypoint method="onCreate">
      <oplist optype="android.database.sqlite.SQLiteOpenHelper">
        <REQUIRE stmt="specialinvoke $r0.<android.database.sqlite.SQLiteOpenHelper: void <init>(android.content.Context,java.lang.String,android.database.sqlite.SQLiteDatabase$CursorFactory,int)>($r1, "gcmstatus", null, $i0)"/>
        <EDIT stmt="$r0.<org.microg.gms.ui.AskPushPermission: org.microg.gms.gcm.GcmDatabase database> = $r3"/>
        <FINISH stmt="virtualinvoke $r0.<android.app.Activity: void finish()>()"/>
      </oplist>
    </entrypoint>
    <entrypoint method="onStop">
      <oplist optype="android.database.sqlite.SQLiteOpenHelper">
        <RELEASE stmt="virtualinvoke $r4.<android.database.sqlite.SQLiteOpenHelper: void close()>()"/>
      </oplist>
    </entrypoint>
</ErrorUsage>
<PerformanceData>
<PerformanceEntry Name="CallgraphConstructionSeconds" Value="3"/>
<PerformanceEntry Name="TotalRuntimeSeconds" Value="4"/>
<PerformanceEntry Name="MaxMemoryConsumption" Value="589"/>
<PerformanceEntry Name="CFGEdge" Value="9343"/>
<PerformanceEntry Name="AllEntryclass" Value="13"/>
<PerformanceEntry Name="AllEntrypoint" Value="51"/>
<PerformanceEntry Name="AllEntryField" Value="143"/>
<PerformanceEntry Name="ReportEntryclass" Value="1"/>
<PerformanceEntry Name="ReportEntryField" Value="15"/>
</PerformanceData>
```
Here the datas in 'PatternData' represents all the execution paths traversed in the data-flow analysis of VALA, and all possibile resource utilization operation sequences summarised for these executions paths; they are sorted by their activities, the target member fields and the target entrypoints in turn.

Here each 'ErrorUsage' represents a resource utilization bug revealed by VALA, and 'violatedPattern' here means the variant lifecycle it occurs in, 'field' means the member field the resource is stored in, and 'oplist' gives a short view of the harmful operation usages.

Next the 'PerformanceData' gives the detailed information of time (by second) and memory (by MB) used by VALA on this app. To note, the time of 'TotalRuntimeSeconds' is the total used in the analysis, while 'CallgraphConstructionSeconds' represents the CFG construction time used to support the analysis of VALA, and their distance represents the time used in the analysis of VALA.


