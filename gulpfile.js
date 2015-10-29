// http://engineroom.teamwork.com/hassle-free-third-party-dependencies/
var gulp = require('gulp');
var concat = require('gulp-concat');
var rename = require('gulp-rename');
var base = 'bower_components/**/'

gulp.task('default', function() {
  gulp.src(['dist/jquery', 'jquery-ui', 'react', 'react-dom', 'husl', 'spin',
            'bootstrap'].map(function(n) { return base + n + '.min.js' })
           .concat([base + 'underscore-min.js']))
    .pipe(concat('vendor.js'))
    .pipe(gulp.dest('resources/public/js/'))

  gulp.src(base + 'bootstrap.min.css')
    .pipe(rename(function(path) {
      path.dirname = "";
      return path;
    }))
    .pipe(gulp.dest('resources/public/css/'))

  gulp.src(base + 'glyphicons-halflings*')
    .pipe(rename(function(path) {
      path.dirname = "";
      return path;
    }))
    .pipe(gulp.dest('resources/public/fonts/'))
});
