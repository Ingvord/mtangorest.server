/*
 * Copyright 2019 Tango Controls
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.tango.rest.v10;

import fr.esrf.Tango.DevFailed;
import org.tango.rest.v10.entities.pipe.Pipe;
import org.tango.rest.v10.entities.pipe.PipeValue;
import org.tango.utils.DevFailedUtils;
import org.tango.web.server.binding.DynamicValue;
import org.tango.web.server.binding.Partitionable;
import org.tango.web.server.binding.RequiresTangoSelector;
import org.tango.web.server.binding.StaticValue;
import org.tango.web.server.proxy.Proxies;
import org.tango.web.server.response.TangoPipeValue;
import org.tango.web.server.util.TangoRestEntityUtils;
import org.tango.web.server.util.TangoSelector;

import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.UriInfo;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * @author ingvord
 * @since 11/19/18
 */
@Path("/pipes")
@Produces(MediaType.APPLICATION_JSON)
public class JaxRsTangoPipes {

    @GET
    @RequiresTangoSelector
    @StaticValue
    public List<Pipe> get(@Context TangoSelector selector, @Context UriInfo uriInfo){
        return selector.selectPipesStream()
                .map(tangoPipeProxy -> TangoRestEntityUtils.newPipe(tangoPipeProxy, uriInfo))
                .collect(Collectors.toList());
    }

    @GET
    @Partitionable
    @RequiresTangoSelector
    @DynamicValue
    @Path("/value")
    public List<PipeValue> getValues(@Context TangoSelector selector, @Context UriInfo uriInfo){
        return selector.selectPipesStream()
                .map(TangoRestEntityUtils::newPipeValue)
                .collect(Collectors.toList());
    }


    @PUT
    @Partitionable
    @DynamicValue
    @Consumes(MediaType.APPLICATION_JSON)
    @Path("/value")
    public List<PipeValue> putValues(@QueryParam("async") boolean async, List<TangoPipeValue> blobs){
        if(async) {
            CompletableFuture.runAsync(() ->  {
                blobs.forEach(tangoPipeValue -> getWritePipeFunction().apply(tangoPipeValue));
            });
            return null;
        } else
            return blobs.stream().map(getWritePipeFunction()).collect(Collectors.toList());
    }

    private Function<TangoPipeValue, TangoPipeValue> getWritePipeFunction() {
        return pipeValue ->
                Proxies.optionalTangoPipeProxy(pipeValue.host, pipeValue.device, pipeValue.name)
                        .map(tangoPipeProxy -> {
                            try {
                                tangoPipeProxy.write(pipeValue.data);
                            } catch (DevFailed devFailed) {
                                pipeValue.errors = devFailed.errors;
                            }
                            return pipeValue;
                        }).orElseGet(() -> {
                    TangoPipeValue failure = new TangoPipeValue();
                    failure.errors = DevFailedUtils.buildDevError(
                            String.format("Failed to get TangoPipeProxy for pipe %s/%s/%s",pipeValue.host,pipeValue.device,pipeValue.name)
                            ,""
                            ,0);
                    return failure;
                });
    }
}
