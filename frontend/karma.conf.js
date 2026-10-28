// Karma config with a CI-friendly headless Chrome launcher (no sandbox inside containers).
module.exports = function (config) {
  config.set({
    basePath: '',
    frameworks: ['jasmine', '@angular-devkit/build-angular'],
    plugins: [require('karma-jasmine'), require('karma-chrome-launcher'), require('karma-jasmine-html-reporter'),
      require('karma-coverage'), require('@angular-devkit/build-angular/plugins/karma')],
    client: { jasmine: {}, clearContext: false },
    reporters: ['progress'],
    browsers: ['ChromeHeadlessCI'],
    customLaunchers: { ChromeHeadlessCI: { base: 'ChromeHeadless', flags: ['--no-sandbox', '--disable-gpu'] } },
    singleRun: true,
    restartOnFileChange: false,
  });
};
