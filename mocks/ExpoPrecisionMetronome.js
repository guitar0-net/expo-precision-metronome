// SPDX-FileCopyrightText: 2026 Andrey Kotlyar <kotlyar562@gmail.com>
//
// SPDX-License-Identifier: MIT

module.exports = {
  start: jest.fn(async () => {}),
  stop: jest.fn(async () => {}),
  pause: jest.fn(async () => {}),
  resume: jest.fn(async () => {}),
  // The stopped snapshot is the honest default: nothing has been started yet.
  getState: jest.fn(async () => ({
    state: "stopped",
    bpm: 0,
    notificationVisible: false,
  })),
  setBpm: jest.fn(async () => {}),
  setSound: jest.fn(async () => {}),
  setPattern: jest.fn(async () => {}),
};
