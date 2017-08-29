package io.shunters.coda.processor;

import com.cedarsoftware.util.io.JsonWriter;
import com.lmax.disruptor.EventHandler;
import com.lmax.disruptor.dsl.Disruptor;
import io.shunters.coda.api.service.AvroDeSerService;
import io.shunters.coda.protocol.ClientServerSpec;
import io.shunters.coda.util.DisruptorBuilder;
import io.shunters.coda.util.SingletonUtils;
import org.apache.avro.generic.GenericRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;

/**
 * Created by mykidong on 2016-09-01.
 */
public class RequestProcessor implements EventHandler<BaseMessage.RequestBytesEvent> {

    private static Logger log = LoggerFactory.getLogger(RequestProcessor.class);

    /**
     * avro de-/serialization service.
     */
    private static AvroDeSerService avroDeSerService = SingletonUtils.getClasspathAvroDeSerServiceSingleton();

    /**
     * request event disruptor.
     */
    private Disruptor<BaseMessage.RequestEvent> requestEventDisruptor;

    /**
     * request event translator.
     */
    private BaseMessage.RequestEventTranslator requestEventTranslator;

    /**
     * response event disruptor.
     */
    private Disruptor<BaseMessage.ResponseEvent> responseEventDisruptor;

    /**
     * response event translator.
     */
    private BaseMessage.ResponseEventTranslator responseEventTranslator;

    private static final Object lock = new Object();

    private static RequestProcessor requestProcessor;

    public static RequestProcessor singleton() {
        if (requestProcessor == null) {
            synchronized (lock) {
                if (requestProcessor == null) {
                    requestProcessor = new RequestProcessor();
                }
            }
        }
        return requestProcessor;
    }


    private RequestProcessor() {
        this.requestEventDisruptor = DisruptorBuilder.singleton("AddOffsetProcessor", BaseMessage.RequestEvent.FACTORY, 1024, AddOffsetProcessor.singleton());
        this.requestEventTranslator = new BaseMessage.RequestEventTranslator();

        this.responseEventDisruptor = DisruptorBuilder.singleton("ResponseProcessor", BaseMessage.ResponseEvent.FACTORY, 1024, ResponseProcessor.singleton());
        this.responseEventTranslator = new BaseMessage.ResponseEventTranslator();
    }

    @Override
    public void onEvent(BaseMessage.RequestBytesEvent requestBytesEvent, long l, boolean b) throws Exception {
        String channelId = requestBytesEvent.getChannelId();
        NioSelector nioSelector = requestBytesEvent.getNioSelector();
        ByteBuffer responseBuffer = null;

        short apiKey = requestBytesEvent.getApiKey();
        byte[] messsageBytes = requestBytesEvent.getMessageBytes();

        // avro schema name.
        String schemaName = SingletonUtils.getApiKeyAvroSchemaMapSingleton().getSchemaName(apiKey);

        // deserialize avro bytes message.
        GenericRecord genericRecord = avroDeSerService.deserialize(schemaName, messsageBytes);


        if (apiKey == ClientServerSpec.API_KEY_PRODUCE_REQUEST) {
//            String prettyJson = JsonWriter.formatJson(genericRecord.toString());
//            log.info("produce request message: \n" + prettyJson);


//            // construct base message event.
//            this.baseMessageEventTranslator.setChannelId(baseMessageBytesEvent.getChannelId());
//            this.baseMessageEventTranslator.setNioSelector(baseMessageBytesEvent.getNioSelector());
//            this.baseMessageEventTranslator.setApiKey(baseMessageBytesEvent.getApiKey());
//            this.baseMessageEventTranslator.setApiVersion(baseMessageBytesEvent.getApiVersion());
//            this.baseMessageEventTranslator.setMessageFormat(baseMessageBytesEvent.getMessageFormat());
//            this.baseMessageEventTranslator.setGenericRecord(genericRecord);
//
//            // send base message event to disruptor.
//            this.baseMessageEventDisruptor.publishEvent(this.baseMessageEventTranslator);


            // TODO: build response message to respond back to client.
            //       IT IS JUST TEST PURPOSE, which must be removed in future.
            byte[] testResponse = "hello, I'm response!".getBytes();
            responseBuffer = ByteBuffer.allocate(testResponse.length);
            responseBuffer.put(testResponse);
            responseBuffer.rewind();

        }
        // TODO: add another api implementation.
        else {
            // TODO:
        }

        // send response event to response disruptor.
        this.responseEventTranslator.setChannelId(channelId);
        this.responseEventTranslator.setNioSelector(nioSelector);
        this.responseEventTranslator.setResponseBuffer(responseBuffer);

        this.responseEventDisruptor.publishEvent(this.responseEventTranslator);
    }
}
