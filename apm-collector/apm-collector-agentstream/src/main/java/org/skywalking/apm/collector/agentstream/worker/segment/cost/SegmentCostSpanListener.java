package org.skywalking.apm.collector.agentstream.worker.segment.cost;

import java.util.ArrayList;
import java.util.List;
import org.skywalking.apm.collector.agentstream.worker.segment.EntrySpanListener;
import org.skywalking.apm.collector.agentstream.worker.segment.GlobalTraceIdsListener;
import org.skywalking.apm.collector.agentstream.worker.segment.cost.define.SegmentCostDataDefine;
import org.skywalking.apm.collector.core.framework.CollectorContextHelper;
import org.skywalking.apm.collector.stream.StreamModuleContext;
import org.skywalking.apm.collector.stream.StreamModuleGroupDefine;
import org.skywalking.apm.collector.stream.worker.WorkerInvokeException;
import org.skywalking.apm.collector.stream.worker.WorkerNotFoundException;
import org.skywalking.apm.network.proto.SpanObject;
import org.skywalking.apm.network.proto.UniqueId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author pengys5
 */
public class SegmentCostSpanListener implements EntrySpanListener, GlobalTraceIdsListener {

    private final Logger logger = LoggerFactory.getLogger(SegmentCostSpanListener.class);

    private List<String> globalTraceIds = new ArrayList<>();
    private List<SegmentCostDataDefine.SegmentCost> segmentCosts = new ArrayList<>();

    @Override
    public void parseEntry(SpanObject spanObject, int applicationId, int applicationInstanceId, String segmentId) {
        SegmentCostDataDefine.SegmentCost segmentCost = new SegmentCostDataDefine.SegmentCost();
        segmentCost.setCost(spanObject.getEndTime() - spanObject.getStartTime());
        segmentCost.setStartTime(spanObject.getStartTime());
        segmentCost.setEndTime(spanObject.getEndTime());
        segmentCost.setSegmentId(segmentId);
        segmentCost.setOperationName(spanObject.getOperationName());
        segmentCosts.add(segmentCost);
    }

    @Override public void parseGlobalTraceId(UniqueId uniqueId) {
        StringBuilder globalTraceIdBuilder = new StringBuilder();
        uniqueId.getIdPartsList().forEach(globalTraceIdBuilder::append);
        globalTraceIds.add(globalTraceIdBuilder.toString());
    }

    @Override public void build() {
        logger.debug("segment cost listener build");
        StreamModuleContext context = (StreamModuleContext)CollectorContextHelper.INSTANCE.getContext(StreamModuleGroupDefine.GROUP_NAME);

        for (SegmentCostDataDefine.SegmentCost segmentCost : segmentCosts) {
            for (String globalTraceId : globalTraceIds) {
                segmentCost.setGlobalTraceId(globalTraceId);
                segmentCost.setId(segmentCost.getSegmentId() + globalTraceId);
                try {
                    logger.debug("send to segment cost persistence worker, id: {}", segmentCost.getId());
                    context.getClusterWorkerContext().lookup(SegmentCostPersistenceWorker.WorkerRole.INSTANCE).tell(segmentCost.transform());
                } catch (WorkerInvokeException | WorkerNotFoundException e) {
                    logger.error(e.getMessage(), e);
                }
            }
        }
    }
}