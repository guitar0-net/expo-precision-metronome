// SPDX-FileCopyrightText: 2026 Andrey Kotlyar <kotlyar562@gmail.com>
//
// SPDX-License-Identifier: MIT

const { defineConfig, globalIgnores } = require("eslint/config");
const universeNative = require("eslint-config-universe/flat/native");

module.exports = defineConfig([
  ...universeNative,
  {
    rules: {
      "@typescript-eslint/no-unused-vars": [
        "warn",
        { argsIgnorePattern: "^_", varsIgnorePattern: "^_" },
      ],
      "prettier/prettier": "error",
      "import/order": [
        "error",
        {
          groups: ["builtin", "external", "internal", ["parent", "sibling", "index"]],
          "newlines-between": "always",
          alphabetize: { order: "asc", caseInsensitive: true },
        },
      ],
      "import/no-duplicates": "error",
    },
  },
  globalIgnores(["build/**", "node_modules/**"]),
]);
