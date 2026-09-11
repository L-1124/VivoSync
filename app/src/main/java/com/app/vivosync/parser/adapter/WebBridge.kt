package com.app.vivosync.parser.adapter

import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.WebView
import com.app.vivosync.model.TimeSlot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

/**
 * Native-JS 双向通信桥（参考开放教务 WebBridge 通信协议规范）
 */
class WebBridge(
    private val scope: CoroutineScope,
    private val onToast: (String) -> Unit,
    private val onShowAlert: (title: String, content: String, confirmText: String?, callback: (Boolean) -> Unit) -> Unit,
    private val onShowSingleSelection: (title: String, items: List<String>, defaultIndex: Int, callback: (Int) -> Unit) -> Unit,
    private val onCoursesImported: (List<ImportCourseJsonModel>) -> Unit,
    private val onConfigImported: ((ImportCourseConfigJsonModel) -> Unit)? = null,
    private val onTimeSlotsImported: ((List<TimeSlot>) -> Unit)? = null,
    private val onTaskCompleted: () -> Unit
) {
    private var webView: WebView? = null

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    fun attachWebView(view: WebView) {
        this.webView = view
    }

    fun detachWebView() {
        this.webView = null
    }

    /**
     * 向 JS Promise 返回响应
     */
    fun resolveJsPromise(callbackId: String, resultRawJs: String = "true") {
        executeOnWebView("window._shiguangNativeCallback('$callbackId', true, $resultRawJs);")
    }

    fun rejectJsPromise(callbackId: String, errorMessage: String) {
        val escaped = errorMessage.replace("'", "\\'").replace("\n", " ")
        executeOnWebView("window._shiguangNativeCallback('$callbackId', false, new Error('$escaped'));")
    }

    private fun executeOnWebView(script: String) {
        scope.launch(Dispatchers.Main) {
            webView?.evaluateJavascript(script, null)
        }
    }

    @JavascriptInterface
    fun postMessage(jsonMessage: String) {
        scope.launch(Dispatchers.Default) {
            runCatching {
                val message = json.decodeFromString<BridgeActionMessage>(jsonMessage)
                val callbackId = message.callbackId
                Log.d(TAG, "[WebBridge] 收到 JS action: ${message.action}, payload: ${message.payload?.take(200)}")

                when (message.action) {
                    "showToast" -> {
                        val payload = message.payload?.let { json.decodeFromString<ShowToastPayload>(it) }
                        val text = payload?.message ?: ""
                        Log.i(TAG, "[WebBridge] JS Toast: $text")
                        if (text.isNotEmpty()) {
                            scope.launch(Dispatchers.Main) { onToast(text) }
                        }
                    }

                    "showAlert" -> {
                        val payload = message.payload?.let { json.decodeFromString<ShowAlertPayload>(it) }
                        val title = payload?.titleText ?: ""
                        val content = payload?.contentText ?: ""
                        val confirmText = payload?.confirmText
                        Log.i(TAG, "[WebBridge] JS Alert: title=$title, content=$content")

                        scope.launch(Dispatchers.Main) {
                            onShowAlert(title, content, confirmText) { confirmed ->
                                resolveJsPromise(callbackId ?: "", confirmed.toString())
                            }
                        }
                    }

                    "showSingleSelection" -> {
                        val payload = message.payload?.let { json.decodeFromString<ShowSingleSelectionPayload>(it) }
                        val title = payload?.titleText ?: ""
                        val items = runCatching {
                            json.decodeFromString<List<String>>(payload?.itemsJsonString ?: "[]")
                        }.getOrDefault(emptyList())
                        val defIdx = payload?.defaultSelectedIndex ?: -1
                        Log.i(TAG, "[WebBridge] JS SingleSelection: title=$title, itemsCount=${items.size}, defaultIdx=$defIdx")

                        scope.launch(Dispatchers.Main) {
                            onShowSingleSelection(title, items, defIdx) { selectedIndex ->
                                resolveJsPromise(callbackId ?: "", selectedIndex.toString())
                            }
                        }
                    }

                    "saveCourseConfig" -> {
                        val payload = message.payload?.let { json.decodeFromString<SaveCourseConfigPayload>(it) }
                        val configJson = payload?.configJsonString ?: "{}"
                        Log.i(TAG, "[WebBridge-Date] 收到 JS 课表开学配置原始数据: $configJson")
                        val config = runCatching {
                            json.decodeFromString<ImportCourseConfigJsonModel>(configJson)
                        }.getOrNull()

                        Log.i(TAG, "[WebBridge-Date] 结构化课表配置 -> 开学日期(semesterStartDate)=${config?.semesterStartDate}, 学期总周数(totalWeeks)=${config?.semesterTotalWeeks}, 首日(firstDayOfWeek)=${config?.firstDayOfWeek}")

                        scope.launch(Dispatchers.Main) {
                            if (config != null) {
                                onConfigImported?.invoke(config)
                            }
                            if (callbackId != null) {
                                resolveJsPromise(callbackId, "true")
                            }
                        }
                    }

                    "savePresetTimeSlots" -> {
                        val payload = message.payload?.let { json.decodeFromString<SaveTimeSlotsPayload>(it) }
                        val slotsJson = payload?.timeSlotsJsonString ?: "[]"
                        Log.i(TAG, "[WebBridge-Time] 收到 JS 节次作息配置原始数据: $slotsJson")
                        val timeSlots = runCatching {
                            json.decodeFromString<List<TimeSlot>>(slotsJson)
                        }.getOrElse {
                            emptyList()
                        }
                        Log.i(TAG, "[WebBridge-Time] 成功解析到作息节次数: ${timeSlots.size}")

                        scope.launch(Dispatchers.Main) {
                            if (timeSlots.isNotEmpty()) {
                                onTimeSlotsImported?.invoke(timeSlots)
                            }
                            if (callbackId != null) {
                                resolveJsPromise(callbackId, "true")
                            }
                        }
                    }

                    "saveImportedCourses" -> {
                        val payload = message.payload?.let { json.decodeFromString<SaveCoursesPayload>(it) }
                        val coursesJson = payload?.coursesJsonString ?: "[]"
                        val courses = runCatching {
                            json.decodeFromString<List<ImportCourseJsonModel>>(coursesJson)
                        }.getOrElse {
                            emptyList()
                        }

                        Log.i(TAG, "[WebBridge-Courses] 解析到课程总数: ${courses.size}")
                        courses.take(10).forEachIndexed { index, c ->
                            Log.i(TAG, "  [课程#$index] 名称='${c.name}' | 星期(day)=${c.day} | 节次=${c.startSection}~${c.endSection} | 周次(weeks)=${c.weeks} | 教室='${c.position}' | 教师='${c.teacher}'")
                        }

                        scope.launch(Dispatchers.Main) {
                            if (courses.isNotEmpty()) {
                                onCoursesImported(courses)
                                onToast("成功解析到 ${courses.size} 门课程")
                                if (callbackId != null) {
                                    resolveJsPromise(callbackId, "true")
                                }
                            } else {
                                onToast("未解析到任何有效课程，请确认课表页面是否已完成查询")
                                if (callbackId != null) {
                                    rejectJsPromise(callbackId, "未解析到课程")
                                }
                            }
                        }
                    }

                    "notifyTaskCompletion" -> {
                        Log.i(TAG, "[WebBridge] 收到 JS notifyTaskCompletion 信号")
                        scope.launch(Dispatchers.Main) {
                            onTaskCompleted()
                        }
                    }

                    else -> {
                        Log.w(TAG, "[WebBridge] 收到未显式处理的 action: ${message.action}")
                        if (callbackId != null) {
                            resolveJsPromise(callbackId, "null")
                        }
                    }
                }
            }.onFailure { e ->
                Log.e(TAG, "[WebBridge] 脚本通信解析异常: ${e.message}", e)
                scope.launch(Dispatchers.Main) {
                    onToast("脚本通信解析异常: ${e.message}")
                }
            }
        }
    }

    companion object {
        private const val TAG = "VivoSync_Import"
        const val BRIDGE_NAME = "_shiguangNativeBridge"

        val JS_BRIDGE_INIT: String = """
        (function() {
            if (window._shiguangBridgeInjected) return;
            window._shiguangBridgeInjected = true;

            var callbacks = {};
            var callbackCounter = 0;

            function postRawMessage(msg) {
                if (window._shiguangNativeBridge && typeof window._shiguangNativeBridge.postMessage === 'function') {
                    window._shiguangNativeBridge.postMessage(msg);
                    return;
                }
                console.warn("[ShiguangBridge] Native bridge unavailable:", msg);
            }

            function postMessageToNative(action, payload, callbackId) {
                var msg = JSON.stringify({
                    action: action,
                    callbackId: callbackId || null,
                    payload: payload ? JSON.stringify(payload) : null
                });
                postRawMessage(msg);
            }

            window._shiguangNativeCallback = function(callbackId, isSuccess, result) {
                var cb = callbacks[callbackId];
                if (cb) {
                    if (isSuccess) {
                        cb.resolve(result);
                    } else {
                        cb.reject(result);
                    }
                    delete callbacks[callbackId];
                }
            };

            var shiguangBridgePromise = {
                showAlert: function(titleText, contentText, confirmText) {
                    return new Promise(function(resolve, reject) {
                        var id = 'cb_' + (++callbackCounter) + '_' + Date.now();
                        callbacks[id] = { resolve: resolve, reject: reject };
                        postMessageToNative('showAlert', {
                            titleText: titleText || '',
                            contentText: contentText || '',
                            confirmText: confirmText || null
                        }, id);
                    });
                },
                showPrompt: function(titleText, tipText, defaultText, validatorJsFunction) {
                    return new Promise(function(resolve, reject) {
                        var id = 'cb_' + (++callbackCounter) + '_' + Date.now();
                        callbacks[id] = { resolve: resolve, reject: reject };
                        postMessageToNative('showPrompt', {
                            titleText: titleText || '',
                            tipText: tipText || '',
                            defaultText: defaultText || '',
                            validatorJsFunction: validatorJsFunction || ''
                        }, id);
                    });
                },
                showSingleSelection: function(titleText, items, defaultSelectedIndex) {
                    return new Promise(function(resolve, reject) {
                        var id = 'cb_' + (++callbackCounter) + '_' + Date.now();
                        callbacks[id] = { resolve: resolve, reject: reject };
                        var itemsJson = (typeof items === 'string') ? items : JSON.stringify(items || []);
                        postMessageToNative('showSingleSelection', {
                            titleText: titleText || '',
                            itemsJsonString: itemsJson,
                            defaultSelectedIndex: defaultSelectedIndex !== undefined ? defaultSelectedIndex : -1
                        }, id);
                    });
                },
                saveImportedCourses: function(coursesJsonString) {
                    return new Promise(function(resolve, reject) {
                        var id = 'cb_' + (++callbackCounter) + '_' + Date.now();
                        callbacks[id] = { resolve: resolve, reject: reject };
                        var jsonStr = (typeof coursesJsonString === 'string') ? coursesJsonString : JSON.stringify(coursesJsonString || []);
                        postMessageToNative('saveImportedCourses', { coursesJsonString: jsonStr }, id);
                    });
                },
                saveCourseConfig: function(configJsonString) {
                    return new Promise(function(resolve, reject) {
                        var id = 'cb_' + (++callbackCounter) + '_' + Date.now();
                        callbacks[id] = { resolve: resolve, reject: reject };
                        var jsonStr = (typeof configJsonString === 'string') ? configJsonString : JSON.stringify(configJsonString || {});
                        postMessageToNative('saveCourseConfig', { configJsonString: jsonStr }, id);
                    });
                },
                savePresetTimeSlots: function(timeSlotsJsonString) {
                    return new Promise(function(resolve, reject) {
                        var id = 'cb_' + (++callbackCounter) + '_' + Date.now();
                        callbacks[id] = { resolve: resolve, reject: reject };
                        var jsonStr = (typeof timeSlotsJsonString === 'string') ? timeSlotsJsonString : JSON.stringify(timeSlotsJsonString || []);
                        postMessageToNative('savePresetTimeSlots', { timeSlotsJsonString: jsonStr }, id);
                    });
                }
            };

            var shiguangBridge = {
                showToast: function(message) {
                    postMessageToNative('showToast', { message: message });
                },
                notifyTaskCompletion: function() {
                    postMessageToNative('notifyTaskCompletion');
                }
            };

            window.shiguangBridgePromise = shiguangBridgePromise;
            window.shiguangBridge = shiguangBridge;
            window.AndroidBridgePromise = shiguangBridgePromise;
            window.AndroidBridge = shiguangBridge;
        })();
        """.trimIndent()
    }
}
