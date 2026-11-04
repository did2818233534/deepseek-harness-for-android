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
if (!Array.prototype.with)
  Object.defineProperty(Array.prototype, "with", {
    configurable: true, writable: true,
    value: function (index, value) {
      if (this == null) throw new TypeError("Array.with called on null or undefined");
      const source = Object(this);
      const length = Math.min(Math.max(Math.trunc(+source.length) || 0, 0), Number.MAX_SAFE_INTEGER);
      const integer = Math.trunc(+index) || 0;
      const actual = integer < 0 ? length + integer : integer;
      if (actual < 0 || actual >= length) throw new RangeError("Array.with index out of range");
      const result = new Array(length);
      for (let i = 0; i < length; i++) result[i] = i === actual ? value : source[i];
      return result;
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
