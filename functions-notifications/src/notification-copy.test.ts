import assert from "node:assert/strict";
import {readFileSync} from "node:fs";
import test from "node:test";

import {COPY} from "./notification-domain.js";

/**
 * The server's English is the fallback a phone shows when it lacks a key, and
 * the key is what every newer phone shows. They must say the same thing, so
 * this reads the app's own resource file (run from functions-notifications).
 */
function appStrings(folder: string): Map<string, string> {
  const xml = readFileSync(`../app/src/main/res/${folder}/strings_notifications.xml`, "utf8");
  return new Map([...xml.matchAll(/<string name="([a-z0-9_]+)">(.*?)<\/string>/g)]
    .map((match) => [match[1], match[2].replace(/\\'/g, "'")]));
}

test("every push string the server names exists in the app, word for word", () => {
  const english = appStrings("values");
  for (const [key, text] of Object.entries(COPY)) {
    assert.equal(english.get(key), text, key);
  }
});

test("the app has no push string the server never sends", () => {
  const english = appStrings("values");
  const unused = [...english.keys()].filter((key) => key.startsWith("push_") && !(key in COPY));
  assert.deepEqual(unused, []);
});

test("Spanish and Hindi have every push string", () => {
  for (const folder of ["values-es", "values-hi"]) {
    const strings = appStrings(folder);
    for (const key of Object.keys(COPY)) assert.ok(strings.has(key), `${folder} ${key}`);
  }
});
