package org.tango.web.server.rest;

/**
 * @author Ingvord
 * @since 21.06.14
 */

import com.google.common.base.Function;
import com.google.common.collect.Collections2;
import fr.esrf.TangoApi.AttributeInfoEx;
import fr.esrf.TangoApi.CommandInfo;
import fr.esrf.TangoApi.DeviceInfo;
import org.apache.commons.beanutils.ConvertUtils;
import org.javatuples.Triplet;
import org.jboss.resteasy.annotations.cache.NoCache;
import org.tango.client.ez.attribute.Quality;
import org.tango.client.ez.proxy.*;
import org.tango.web.rest.DeviceState;
import org.tango.web.rest.Response;
import org.tango.web.rest.Responses;
import org.tango.web.server.DatabaseDs;
import org.tango.web.server.DeviceMapper;

import javax.annotation.Nullable;
import javax.servlet.ServletContext;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

@Path("/")
@NoCache
public class Rest2Tango {
    @GET
    @Path("devices")
    @Produces("application/json")
    public Response getDevices(@Context ServletContext ctx) throws Exception {//TODO handle exception
        DatabaseDs db = (DatabaseDs) ctx.getAttribute(DatabaseDs.TANGO_DB);
        return Responses.createSuccessResult(db.getDeviceList());
    }

    @GET
    @Path("device/{domain}/{name}/{instance}")
    @Produces("application/json")
    public DeviceState getDevice(@PathParam("domain") String domain,
                                 @PathParam("name") String name,
                                 @PathParam("instance") String instance,
                                 @Context ServletContext ctx) throws Exception {//TODO exceptions
        TangoProxy proxy = lookupTangoProxy(domain, name, instance, ctx);
        DeviceState result = new DeviceState(proxy.toDeviceProxy().state().toString(), proxy.toDeviceProxy().status());
        return result;
    }

    @GET
    @Path("device/{domain}/{name}/{instance}/info")
    @Produces("application/json")
    public DeviceInfo getDeviceInfo(@PathParam("domain") String domain,
                                    @PathParam("name") String name,
                                    @PathParam("instance") String instance,
                                    @Context ServletContext ctx) throws Exception {//TODO exceptions
        DatabaseDs db = (DatabaseDs) ctx.getAttribute(DatabaseDs.TANGO_DB);
        DeviceInfo info = db.getDeviceInfo(domain + "/" + name + "/" + instance);
        return info;
    }

    @GET
    @Path("device/{domain}/{name}/{instance}/attributes")
    @Produces("application/json")
    public Response getDeviceAttributes(@PathParam("domain") String domain,
                                        @PathParam("name") String name,
                                        @PathParam("instance") String instance,
                                        @Context ServletContext ctx) throws Exception {
        TangoProxy proxy = lookupTangoProxy(domain, name, instance, ctx);
        Collection<String> result = Collections2.transform(Arrays.asList(proxy.toDeviceProxy().get_attribute_info_ex()), new Function<AttributeInfoEx, String>() {
            @Override
            public String apply(@Nullable AttributeInfoEx input) {
                return input.name;
            }
        });
        return Responses.createSuccessResult(result);
    }

    @GET
    @Path("device/{domain}/{name}/{instance}/commands")
    @Produces("application/json")
    public Response getDeviceCommands(@PathParam("domain") String domain,
                                      @PathParam("name") String name,
                                      @PathParam("instance") String instance,
                                      @Context ServletContext ctx) throws Exception {
        TangoProxy proxy = lookupTangoProxy(domain, name, instance, ctx);
        Collection<String> result = Collections2.transform(Arrays.asList(proxy.toDeviceProxy().command_list_query()), new Function<CommandInfo, String>() {
            @Override
            public String apply(@Nullable CommandInfo input) {
                return input.cmd_name;
            }
        });
        return Responses.createSuccessResult(result);
    }

    @PUT
    @Path("device/{domain}/{name}/{instance}/{member}={arg}")
    @Produces("application/json")
    public Response putAttribute(@PathParam("domain") String domain,
                                 @PathParam("name") String name,
                                 @PathParam("instance") String instance,
                                 @PathParam("member") String member,
                                 @PathParam("arg") String arg,
                                 @Context ServletContext ctx) throws Exception {
        TangoProxy proxy = lookupTangoProxy(domain, name, instance, ctx);
        if (proxy.hasCommand(member)) {
            Class<?> targetType = proxy.getCommandInfo(member).getArginType();
            if (targetType == Void.class) return Responses.createSuccessResult(proxy.executeCommand(member, null));
            Object converted = ConvertUtils.convert(arg, targetType);
            return Responses.createSuccessResult(proxy.executeCommand(member, converted));
        } else if (proxy.hasAttribute(member)) {
            Class<?> targetType = proxy.getAttributeInfo(member).getClazz();
            Object converted = ConvertUtils.convert(arg, targetType);
            proxy.writeAttribute(member, converted);
            return Responses.createSuccessResult(null);
        } else
            throw new IllegalArgumentException(String.format("Device %s does not have neither attribute nor command %s", proxy.getName(), member));
    }

    @GET
    @Path("device/{domain}/{name}/{instance}/{cmd_or_attr}/info")
    @Produces("application/json")
    public Object getCommandOrAttributeInfo(@PathParam("domain") String domain,
                                            @PathParam("name") String name,
                                            @PathParam("instance") String instance,
                                            @PathParam("cmd_or_attr") String member,
                                            @Context ServletContext ctx) throws Exception {
        TangoProxy proxy = lookupTangoProxy(domain, name, instance, ctx);
        if (proxy.hasAttribute(member))
            return proxy.getAttributeInfo(member).toAttributeInfo();
        else
            return proxy.getCommandInfo(member).toCommandInfo();
    }

    @GET
    @Path("device/{domain}/{name}/{instance}/{cmd_or_attr}")
    @Produces("application/json")
    public Object getCommandOrAttribute(@PathParam("domain") String domain,
                                        @PathParam("name") String name,
                                        @PathParam("instance") String instance,
                                        @PathParam("cmd_or_attr") String member,
                                        @Context ServletContext ctx) throws Exception {

        TangoProxy proxy = lookupTangoProxy(domain, name, instance, ctx);
        if (proxy.hasAttribute(member)) //TODO if attr image - generate one and send link
        {
            Triplet<Object, Long, Quality> result = proxy.readAttributeValueTimeQuality(member);
            return Responses.createAttributeSuccessResult(new Triplet<>(result.getValue0(), result.getValue1(), result.getValue2().name()));
        } else if (proxy.hasCommand(member))
            return Responses.createSuccessResult(proxy.executeCommand(member, null));
        else
            throw new IllegalArgumentException();
    }

    @GET
    @Path("device/{domain}/{name}/{instance}/{attr}.{evt}")
    @Produces("application/json")
    public Object getCommandOrAttributeOnChange(@PathParam("domain") String domain,
                                        @PathParam("name") String name,
                                        @PathParam("instance") String instance,
                                        @PathParam("attr") String member,
                                        @PathParam("evt") String evt,
                                        @Context ServletContext ctx) throws Exception {

        TangoProxy proxy = lookupTangoProxy(domain, name, instance, ctx);
        if (!proxy.hasAttribute(member)) return Responses.createFailureResult(new String[]{String.format("No such attr: %s/%s/%s/%s",domain,name,instance,member)});

        try {
            TangoEvent event = TangoEvent.valueOf(evt.toUpperCase());
            proxy.subscribeToEvent(member, event);

            final AtomicReference<Response<Object>> refResult = new AtomicReference<>();
            final CountDownLatch latch = new CountDownLatch(1);
            TangoEventListener<Object> listener = new TangoEventListener<Object>() {
                @Override
                public void onEvent(EventData<Object> data) {
                    refResult.set(Responses.createAttributeSuccessResult(new Triplet<>(data.getValue(), data.getTime(), Quality.VALID.name())));
                    latch.countDown();
                }

                @Override
                public void onError(Throwable cause) {
                    refResult.set(Responses.createFailureResult(new String[]{cause.getMessage()}));
                    latch.countDown();
                }
            };
            proxy.addEventListener(member, event, listener);

            //block this servlet until event
            latch.await(Long.MAX_VALUE, TimeUnit.MILLISECONDS);
            return refResult.get();
        } catch (Exception e) {
            return Responses.createFailureResult(new String[]{e.getMessage()});
        }
    }

    @GET
    @Path("device/{domain}/{name}/{instance}/{cmd_or_attr}={arg}")//TODO optional argument
    @Produces("application/json")
    public Object getCommand(@PathParam("domain") String domain,
                             @PathParam("name") String name,
                             @PathParam("instance") String instance,
                             @PathParam("cmd_or_attr") String cmd,
                             @PathParam("arg") String arg,
                             @QueryParam("_method") String method,
                             @Context ServletContext ctx) throws Exception {//TODO exceptions
        TangoProxy proxy = lookupTangoProxy(domain, name, instance, ctx);
        if (!proxy.hasCommand(cmd)) {
            //workaround jsonp limitation
            if(method != null && "PUT".equalsIgnoreCase(method) && proxy.hasAttribute(cmd)){
                Class<?> targetType = proxy.getAttributeInfo(cmd).getType().getDataType();
                Object converted = ConvertUtils.convert(arg, targetType);
                proxy.writeAttribute(cmd,converted);
                return Responses.createSuccessResult(null);
            } else
                throw new IllegalArgumentException(String.format("Device %s does not have command %s", proxy.getName(), cmd));
        } else {
            Class<?> targetType = proxy.getCommandInfo(cmd).getArginType();
            if (targetType == Void.class) return proxy.executeCommand(cmd, null);
            Object converted = ConvertUtils.convert(arg, targetType);
            return Responses.createSuccessResult(proxy.executeCommand(cmd, converted));
        }
    }

    private TangoProxy lookupTangoProxy(String domain, String name, String instance, ServletContext ctx) throws TangoProxyException {
        DeviceMapper mapper = (DeviceMapper) ctx.getAttribute(DeviceMapper.TANGO_MAPPER);
        return mapper.map(domain + "/" + name + "/" + instance);
    }
}
