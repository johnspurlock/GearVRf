/* Copyright 2016 Samsung Electronics Co., LTD
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

package org.gearvrf;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import org.gearvrf.script.GVRScriptFile;
import org.gearvrf.script.IScriptable;

/**
 * This class provides API for event-related operations in the
 * framework. In the framework, events are categorized into
 * groups, each of which is represented by an interface extending
 * the tag interface {@link IEvents}. For example,
 * the event group {@link IScriptEvents} contain life-cycle events
 * {@link IScriptEvents#onInit(GVRContext)}, and per-frame callbacks
 * {@link IScriptEvents#onStep()}.<p>
 *
 * Other event groups can be defined in similar ways. As seen above,
 * we don't necessarily use classes to represent events themselves,
 * but we may create event classes (such as {@code MouseEvent}) to
 * represent details of an event, and pass it to an event handler.<p>
 *
 * An event handler can take one of the two forms. 1) It can be a
 * class implementing an event interface in Java. 2) It can be a
 * script in a scripting language, such as Lua and Javascript. In
 * the scripting case, the prototype of the function mirrors their
 * Java counterpart. For example, a handler function in Lua for
 * {@link IScriptEvents#onInit(GVRContext)} is<p>
 *
 * <pre>
 * {@code
 * function onInit(gvrf)
 *    ...
 * end
 * }
 * </pre>
 */
public class GVREventManager {
    private static final String TAG = GVREventManager.class.getSimpleName();
    private GVRContext mGvrContext;

    GVREventManager(GVRContext gvrContext) {
        mGvrContext = gvrContext;
    }

    /**
     * Delivers an event to a handler object.
     *
     * @param target
     *     The object which handles the event.
     * @param eventsClass
     *     The interface class object representing an event group, such
     *     as {@link IScriptEvents}.class.
     * @param eventName
     *     The name of the event, such as "onInit".
     * @param params
     *     Parameters of the event. It should match the parameter list
     * of the corresponding method in the interface, specified by {@code
     * event class}
     */
    public void sendEvent(Object target, Class<? extends IEvents> eventsClass,
            String eventName, Object... params) {
        // Validate the event
        Method method = validateEvent(target, eventsClass, eventName, params);

        // Try invoking the handler in the script
        if (target instanceof IScriptable) {
            tryInvokeScript((IScriptable)target, eventName, params);
        }

        // Try invoking the method in target
        invokeMethod(target, method, params);
    }

    private Method validateEvent(Object target, Class<? extends IEvents> eventsClass,
            String eventName, Object[] params) {
        // Check target event interface
        if (!eventsClass.isInstance(target)) {
            throw new RuntimeException(String.format("The target object does not implement interface %s",
                    eventsClass.getSimpleName()));
        }

        Method nameMatch = null;
        for (Method method : eventsClass.getMethods()) {
            // Match method name and event name
            if (method.getName().equals(eventName)) {
                nameMatch = method;

                // Check number of parameters
                Class<?>[] types = method.getParameterTypes();
                if (types.length != params.length)
                    continue;

                // Check parameter types
                int i = 0;
                boolean foundMatchedMethod = true;
                for (Class<?> type : types) {
                    Object param = params[i++];
                    if (!type.isInstance(param)) {
                        foundMatchedMethod = false;
                        break;
                    }
                }

                if (foundMatchedMethod)
                    return method;
            }
        }

        // Error
        if (nameMatch != null) {
            throw new RuntimeException(String.format("The target object contains a method %s but parameters don't match",
                    eventName));
        } else {
            throw new RuntimeException(String.format("The target object has no method %s",
                    eventName));
        }
    }

    private void tryInvokeScript(IScriptable target, String eventName,
            Object[] params) {
        GVRScriptFile script = mGvrContext.getScriptManager().getScriptFile(target);
        if (script == null)
            return;

        script.invokeFunction(eventName, params);
    }

    private void invokeMethod(Object target, Method method, Object[] params) {
        try {
            method.invoke(target, params);
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (IllegalArgumentException e) {
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            Throwable throwable = e.getCause();
            // rethrow the RuntimeException back to the application
            if (throwable instanceof RuntimeException) {
                throw (RuntimeException) throwable;
            } else {
                e.printStackTrace();
            }
        }
    }
}
