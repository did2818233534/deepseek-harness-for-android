// Exercise the Android Node 18 compatibility path even on a newer desktop Node.
for (const name of ['with', 'toSorted', 'toReversed', 'toSpliced']) delete Array.prototype[name];
delete Promise.withResolvers;
