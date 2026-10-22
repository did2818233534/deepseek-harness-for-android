import { webcrypto } from "node:crypto";
if (!globalThis.crypto) globalThis.crypto = webcrypto;
// Compatibility for the pinned Android Node runtime. No OS or CPU emulation.
if (!Array.prototype.toSorted)
  Object.defineProperty(Array.prototype, "toSorted", {
    value: function (compare) {
      return [...this].sort(compare);
    },
  });
if (!Array.prototype.toReversed)
  Object.defineProperty(Array.prototype, "toReversed", {
    value: function () {
      return [...this].reverse();
    },
  });
if (!Array.prototype.toSpliced)
  Object.defineProperty(Array.prototype, "toSpliced", {
    value: function (...args) {
      const copy = [...this];
      copy.splice(...args);
      return copy;
    },
  });
if (!Promise.withResolvers)
  Promise.withResolvers = function () {
    let resolve, reject;
    const promise = new Promise((a, b) => {
      resolve = a;
      reject = b;
    });
    return { promise, resolve, reject };
  };
if (!Symbol.dispose)
  Object.defineProperty(Symbol, "dispose", {
    value: Symbol.for("nodejs.dispose"),
  });
if (!Symbol.asyncDispose)
  Object.defineProperty(Symbol, "asyncDispose", {
    value: Symbol.for("nodejs.asyncDispose"),
  });
