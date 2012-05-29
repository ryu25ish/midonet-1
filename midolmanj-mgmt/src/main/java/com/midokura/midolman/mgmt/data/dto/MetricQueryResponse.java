/*
 * Copyright (c) 2012 Midokura Pte.Ltd.
 */

package com.midokura.midolman.mgmt.data.dto;

import java.util.Map;
import javax.xml.bind.annotation.XmlRootElement;

/**
 * Date: 5/3/12
 */
@XmlRootElement
public class MetricQueryResponse extends UriResource {

    String interfaceName;
    String metricName;
    long timeStampStart;
    long timeStampEnd;
    Map<String, Long> results;
    String type;


    public MetricQueryResponse() {
    }

    public void setMetricName(String metricName) {
        this.metricName = metricName;
    }

    public void setTimeStampStart(long timeStampStart) {
        this.timeStampStart = timeStampStart;
    }

    public void setTimeStampEnd(long timeStampEnd) {
        this.timeStampEnd = timeStampEnd;
    }

    public void setResults(Map<String, Long> results) {
        this.results = results;
    }

    public void setInterfaceName(String interfaceName) {
        this.interfaceName = interfaceName;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getInterfaceName() {
        return interfaceName;
    }

    public String getMetricName() {
        return metricName;
    }

    public long getTimeStampStart() {
        return timeStampStart;
    }

    public long getTimeStampEnd() {
        return timeStampEnd;
    }

    public Map<String, Long> getResults() {
        return results;
    }

    public String getType() {
        return type;
    }
}
