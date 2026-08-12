// SPDX-FileCopyrightText: 2026 Andrey Kotlyar <kotlyar562@gmail.com>
//
// SPDX-License-Identifier: MIT

// Two projects because the config plugin runs in Node at prebuild time, while the
// library itself runs in React Native — they need different environments.
module.exports = {
  projects: [
    {
      displayName: "js",
      preset: "jest-expo",
      testMatch: ["<rootDir>/src/__tests__/**/*.[jt]s?(x)"],
    },
    {
      displayName: "plugin",
      testEnvironment: "node",
      testMatch: ["<rootDir>/plugin/__tests__/**/*.[jt]s?(x)"],
      transform: {
        "^.+\\.[jt]sx?$": [
          "babel-jest",
          {
            configFile: require.resolve("expo-module-scripts/babel.config.base"),
          },
        ],
      },
    },
  ],
};
