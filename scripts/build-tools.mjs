import path from 'node:path';
import { pathToFileURL } from 'node:url';

// Keep the original workstation default, while allowing account-local toolchains.
export const modules = path.resolve(process.env.DSH_BUILD_TOOLS || '/opt/dsh-android/build-tools', 'node_modules');
export const tool = relative => import(pathToFileURL(path.join(modules, relative)).href);
