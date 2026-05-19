import {initUniappBridge} from "./bridgeClientImpl.uni";
import dsbridge from 'dsbridge';

export function _handleNativeCall(successCB, failCB) {
    return (result) => {
        if (result.code === 0) {
            if (typeof result.data === 'string') {
                try {
                    let obj = JSON.parse(result.data);
                    successCB && successCB(obj);
                } catch (e) {
                    successCB && successCB(result.data);
                }
            } else {
                successCB && successCB(result.data);
            }
        } else {
            failCB && failCB(result.code);
        }
    }
}

// Web fallback bridge：在 iframe 中通过 postMessage 与父页面通信
let webFallbackBridge = null;

function initWebFallbackBridge() {
    if (webFallbackBridge) return webFallbackBridge;

    let callbackMap = new Map();
    let eventListeners = {};
    let requestId = 0;

    window.addEventListener('message', (event) => {
        const data = event.data;
        if (!data || typeof data !== 'object') return;
        if (data.type === 'wf-op-event') {
            eventListeners[data.handlerName] && eventListeners[data.handlerName](data.args);
        } else if (data.type === 'wf-op-response') {
            let cb = callbackMap.get(data.requestId);
            if (cb) {
                cb(data.args);
                callbackMap.delete(data.requestId);
            }
        }
    });

    webFallbackBridge = {
        call: (handlerName, args, callback) => {
            let reqId = 0;
            if (callback && typeof callback === 'function') {
                reqId = ++requestId;
                callbackMap.set(reqId, callback);
            }
            let appUrl = location.href;
            let obj = { type: 'wf-op-request', requestId: reqId, appUrl, handlerName, args };
            window.parent.postMessage(obj, '*');
        },
        register: (handlerName, callback) => {
            eventListeners[handlerName] = callback;
        }
    };
    return webFallbackBridge;
}

export function bridge() {
    if (navigator.userAgent.indexOf('uni-app') >= 0) {
        if (!window.__wf_bridge_) {
            initUniappBridge();
        }
        return window.__wf_bridge_;
    } else if (window.__wf_bridge_) {
        return window.__wf_bridge_;
    } else if (window !== window.parent) {
        // 在 iframe 中，使用 postMessage fallback
        return initWebFallbackBridge();
    } else {
        return dsbridge;
    }
}
