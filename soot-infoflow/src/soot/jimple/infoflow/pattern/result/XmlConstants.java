package soot.jimple.infoflow.pattern.result;

public class XmlConstants {
    public class Tags {

        public static final String root = "DataFlowResults";

        public static final String results = "Results";
        public static final String result = "Result";

        public static final String exception = "Exception";
        public static final String exceptionStmt = "Stmt";


        public static final String performanceData = "PerformanceData";
        public static final String performanceEntry = "PerformanceEntry";

        public static final String sink = "Sink";
        public static final String sinks = "Sinks";
        public static final String accessPath = "AccessPath";

        public static final String fields = "Fields";
        public static final String field = "Field";

        public static final String sources = "Sources";
        public static final String source = "Source";

        public static final String taintPath = "TaintPath";
        public static final String pathElement = "PathElement";

        public static final String patterndatas = "PatternDatas";
        public static final String patterndata = "PatternData";
        public static final String entrypoints = "entrypoints";
        public static final String entrypoint = "entrypoint";
        public static final String shouldCheck = "shouldCheck";
        public static final String fragments = "fragments";
        public static final String fragment = "fragment";

        public static final String entrypointclasses = "entryclasses";
        public static final String entrypointclass = "class";

        public static final String entrypointmethods = "entrymethods";
        public static final String entrypointmethod = "method";

        public static final String targetfields = "fields";
        public static final String targetfield = "field";


        public static final String operationlist = "oplist";
        public static final String operation = "op";

        //final error usages
        public static final String errorusages = "ErrorUsages";
        public static final String errorusage = "ErrorUsage";
    }

    public class Attributes {

        public static final String fileFormatVersion = "FileFormatVersion";
        public static final String terminationState = "TerminationState";
        public static final String statement = "Statement";
        public static final String method = "Method";
        public static final String methodbody = "body";


        public static final String value = "Value";
        public static final String type = "Type";
        public static final String taintSubFields = "TaintSubFields";

        public static final String category = "Category";

        public static final String name = "Name";

        public static final String patterndatatype = "type";


        public static final String shouldCheck = "shouldCheck";
        public static final String minSdk = "minSdk";
        public static final String targetSdk = "targetSdk";
        public static final String fragmentclasses = "fragmentclasses";
        public static final String fragmenttype = "type";
        public static final String fragmentactivityclass = "activityclass";

        public static final String classname = "name";
        public static final String methodsig = "method";
        public static final String fieldname = "field";
        public static final String optype = "optype";
        public static final String stmt = "stmt";

        public static final String violatedPattern = "violatedPattern";


    }

    public class Values {

        public static final String TRUE = "true";
        public static final String FALSE = "false";

        public static final String PERF_CALLGRAPH_SECONDS = "CallgraphConstructionSeconds";
        public static final String PERF_TAINT_PROPAGATION_SECONDS = "TaintPropagationSeconds";
        public static final String PERF_PATH_RECONSTRUCTION_SECONDS = "PathReconstructionSeconds";
        public static final String PERF_TOTAL_RUNTIME_SECONDS = "TotalRuntimeSeconds";
        public static final String PERF_MAX_MEMORY_CONSUMPTION = "MaxMemoryConsumption";

        public static final String PERF_SOURCE_COUNT = "SourceCount";
        public static final String PERF_SINK_COUNT = "SinkCount";
        //our own constants
        public static final String PERF_CFG_EDGE_COUNT = "CFGEdge";
        public static final String PERF_ENTRY_NUM = "Entryponint";
        public static final String PERF_ENTRY_FIELD_NUM = "EntryField";
        //all app data
        public static final String PERF_ALL_ENTRYCLASS_NUM = "AllEntryclass";
        public static final String PERF_ALL_ENTRYPOINT_NUM = "AllEntrypoint";
        public static final String PERF_ALL_ENTRYFIELD_NUM = "AllEntryField";
        //report app data
        public static final String PERF_REPORT_ENTRYCLASS_NUM = "ReportEntryclass";
        public static final String PERF_REPORT_ENTRYPOINT_NUM = "ReportEntrypoint";
        public static final String PERF_REPORT_ENTRYFIELD_NUM = "ReportEntryField";


    }
}
