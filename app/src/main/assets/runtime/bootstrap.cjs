// Keep native startup failures observable instead of letting Node terminate the Android UI process.
const fs = require('fs');
const path = require('path');
const root = process.argv[2];
function failed(error) {
  fs.writeFileSync(path.join(root, 'runtime-error'), String(error.stack || error));
  setInterval(() => {}, 60000);
}
process.on('uncaughtException', failed);
process.on('unhandledRejection', failed);
try { require('./main.cjs'); } catch (error) { failed(error); }
