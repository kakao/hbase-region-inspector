// http://engineroom.teamwork.com/hassle-free-third-party-dependencies/
var gulp = require('gulp');
var concat = require('gulp-concat');
var filter = require('gulp-filter');
var uglify = require('gulp-uglify');
var minify = require('gulp-minify-css');
var mainBowerFiles = require('main-bower-files');

gulp.task('default', function() {
  var mainFiles = mainBowerFiles();

  gulp.src(mainFiles)
    .pipe(filter('**/*.js'))
    .pipe(concat('vendor.js'))
    .pipe(uglify())
    .pipe(gulp.dest('resources/public/js/'))

  gulp.src(mainFiles)
    .pipe(filter('**/*.css'))
    .pipe(minify())
    .pipe(gulp.dest('resources/public/css/'))

  gulp.src(mainFiles)
    .pipe(filter('**/glyphicons*'))
    .pipe(gulp.dest('resources/public/fonts/'))
});
