package com.nexr.interceptor;

import com.google.common.annotations.VisibleForTesting;
import org.apache.flume.Context;
import org.apache.flume.Event;
import org.apache.flume.interceptor.Interceptor;
import org.apache.flume.interceptor.TimestampInterceptor;
import org.apache.flume.source.SpoolDirectorySourceConfigurationConstants;
import org.apache.flume.tools.TimestampRoundDownUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Simple Interceptor class that sets datatype on all events that are intercepted.<p>
 * Extract datatype from fileName during SpoolDir source.<p/>
 *
 * Example:
 * hrelay0_LOG_HTTP_2015XXXX.dat  >> datatype = HTTP
 *
 * <br/>
 * Properties:
 * <ul>
 * datatypeHeader: Specify the key to be used in the event header map for the datatype name. (default is "datatype") <p>
 * </ul>
 *
 * Sample config: <br/>
 * <code>
 * agent.sources.r1.channels = c1<p>
 * agent.sources.r1.type = SEQ<p>
 * agent.sources.r1.interceptors = i1<p>
 * agent.sources.r1.interceptors.i1.type = com.nexr.interceptor.DataTypeExtractorInterceptor$Builder<p>
 * agent.sources.r1.interceptors.i1.datatypeHeader = datatype
 * agent.sources.r1.interceptors.i1.datatype = ${dataType}
 * </code>
 */
public class DataTypeExtractorInterceptor implements Interceptor {

    private static final Logger logger = LoggerFactory.getLogger(DataTypeExtractorInterceptor.class);

    private static final String DEFAULT_TYPE = "NONTYPE";

    private final boolean preserveExisting;
    private final String header;
    private String dataType;

    private String basenameHeader;
    private String baseName;
    private String hmPath;

    /**
     * Only {@link  DataTypeExtractorInterceptor.Builder} can build me
     */
    private DataTypeExtractorInterceptor(boolean preserveExisting, String header, String basenameHeader, String dataType) {
        this.preserveExisting = preserveExisting;
        this.header = header;
        this.basenameHeader = basenameHeader;
        this.dataType = dataType;
    }


    @Override
    public void initialize() {
    }

    @Override
    public Event intercept(Event event) {
        Map<String, String> headers = event.getHeaders();

        if (preserveExisting && headers.containsKey(header)) {
            return event;
        }
        if (headers.get(basenameHeader) != null) {
            if (!headers.get(basenameHeader).equals(baseName)) {
                String old = baseName;
                baseName = headers.get(basenameHeader);
                hmPath = roundDown(headers.get(TimestampInterceptor.Constants.TIMESTAMP));
                logger.info("set baseName {}, {} ", baseName, hmPath);
            }

            if (dataType == null) {
                dataType = extractDataType(headers.get(basenameHeader));
            }

            if (dataType == null) {
                dataType = DEFAULT_TYPE;
            }
        }

        if (dataType != null) {
            headers.put(header, dataType);
        }
        if (hmPath == null) {
            hmPath = roundDown(String.valueOf(System.currentTimeMillis()));
        }
        headers.put(Constants.HMPATH, hmPath);

        return event;
    }

    /**
     * Extract datatype from file name. ex> yongin_LOG_S1AP_201503192356.dat >> S1AP
     * @param fileName
     * @return
     */
    @VisibleForTesting
    String extractDataType(String fileName) {
        String patternString1 = "(LOG_)(.*)(_.*.dat)";

        Pattern pattern = Pattern.compile(patternString1);
        Matcher matcher = pattern.matcher(fileName);

        if (matcher.find()) {
            return matcher.group(2);
        }
        return null;
    }

    @VisibleForTesting
    String roundDown(String time) {
        long timestamp = Long.parseLong(time);
        long roundDowned = TimestampRoundDownUtil.roundDownTimeStampMinutes(timestamp, 10);
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(roundDowned);
        SimpleDateFormat format = new SimpleDateFormat("HHmm");
        String hhmm = format.format(calendar.getTime());
        return hhmm;
    }

    @Override
    public List<Event> intercept(List<Event> events) {
        for (Event event : events) {
            intercept(event);
        }
        return events;
    }

    @Override
    public void close() {
    }

    /**
     * Builder which builds new instances of the DataTypeExtractorInterceptor.
     */
    public static class Builder implements Interceptor.Builder {

        private static final boolean PRESERVE_DFLT = Constants.PRESERVE_DFLT;
        private boolean preserveExisting = PRESERVE_DFLT;
        private String header = Constants.DATATYPE;
        private String baseName = SpoolDirectorySourceConfigurationConstants.DEFAULT_BASENAME_HEADER_KEY;
        private String dataType = null;

        @Override
        public Interceptor build() {
            return new DataTypeExtractorInterceptor(preserveExisting, header, baseName, dataType);
        }

        @Override
        public void configure(Context context) {
            preserveExisting = context.getBoolean(Constants.PRESERVE, PRESERVE_DFLT);
            header = context.getString(Constants.DATATYPE_HEADER, Constants.DATATYPE);
            baseName = context.getString(Constants.BASENAME_HEADER_KEY, SpoolDirectorySourceConfigurationConstants
                    .DEFAULT_BASENAME_HEADER_KEY);
            dataType = context.getString(Constants.DATATYPE, null);
        }
    }

    public static class Constants {
        public static String DATATYPE = "datatype";

        public static String PRESERVE = "preserveExisting";
        public static boolean PRESERVE_DFLT = false;

        public static String DATATYPE_HEADER = "datatypeHeader";
        public static String BASENAME_HEADER_KEY = "basenameHeaderKey";

        public static String HMPATH = "hmpath";
    }
}
