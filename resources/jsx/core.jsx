var refresh = {
  version: 0,
  interval: 10,
  ticks: 0,
  paused: false,
  pause: function() {
    this.paused = true;
  },
  resume: function() {
    this.paused = false;
  },
  timeout: null
};

var colors = {};

function colorFor(table) {
  if (!colors[table]) {
    var h = Math.random() * 360
      , s = 30 + Math.random() * 20
      , l = 60 + Math.random() * 20;
    return colors[table] = [$.husl.toHex(h, s, l), $.husl.toHex(h, s, l - 10)];
  }
  return colors[table];
}

function schedule(job) {
  var tick = function() {
    if (refresh.ticks == refresh.interval) {
      refresh.ticks = 0;
      job();
    } else {
      var sec = refresh.interval - refresh.ticks;
      if (sec > 0) {
        $("#refresh_msg").text("Refresh in " + sec + " second" + (sec > 1 ? "s" : ""));
      }
      if (!refresh.paused) {
        refresh.ticks++;
      }
      refresh.timeout = setTimeout(tick, 1000);
    }
  }
  tick();
}

function fmt(val) {
  if (val > 10 || Math.floor(val) == val) {
    return Math.floor(val);
  }
  return val.toFixed(2);
}

function debug() {
  // console.log.apply(console, arguments);
}

function disablePopover() {
  $(".extra-info").popover('disable')
}

function enablePopover(pos) {
  $(".extra-info").popover({
    trigger:   "hover",
    html:      true,
    placement: pos,
    container: "body"
  });
  $(".extra-info").popover('enable')
}

function refreshApp(menu, opts) {
  clearTimeout(refresh.timeout);

  var url = menu == "rs" ? "/server_regions.json" : "/table_regions.json"
  var currentVersion = refresh.version;
  debug(opts)
  $.ajax({
    url: url,
    data: opts,
    success: function(result) {
      if (refresh.version != currentVersion) {
        debug("already updated: " + currentVersion + "/" + refresh.version);
        return;
      }
      refresh.version++;
      React.render(<App {...opts} menu={menu} result={result}/>, document.body);
      $(".draggable").draggable({
        helper: 'clone',
        revert: 'invalid',
        revertDuration: 200,
        start: function(e, ui) {
          refresh.pause();

          var orig = $(e.target);
          disablePopover();
          ui.helper.width(orig.width()).height(orig.height()).css({
            'border-width':       "2px",
            'border-color':       orig.css("color"),
            'border-style':       'solid',
            '-webkit-transition': 'none',
            '-moz-transition':    'none',
            '-ms-transition':     'none',
            '-o-transition':      'none',
            'transition':         'none'
          });
          orig.hide();
        },
        stop: function(e, ui) {
          debug("Drag stopped");
          $(e.target).show();
          enablePopover(menu == "rs" ? "top" : "right");
        },
        revert: function(valid) {
          if (!valid) {
            refresh.resume();
          }
        }
      });
      $(".droppable").droppable({
        hoverClass: "drop-target",
        drop: function(e, ui) {
          var src    = ui.draggable.parent().data("server");
          var dest   = $(e.target).data("server");

          debug("Dropped ", src, dest);
          if (src == dest) {
            return
          }

          var region = ui.draggable.data("region")
          var modal  = $("#modal");
          var yes    = modal.find(".btn-primary");
          var no     = modal.find(".btn-default");
          var title  = modal.find(".modal-title");
          var body   = modal.find(".modal-body");

          title.html("Move " + region);
          body.html(
            $("<ul>").append($("<li>", { text: "from " + src }))
                     .append($("<li>", { text: "to " + dest })));
          yes.unbind('click');
          yes.click(function(e) {
            $(".draggable").draggable('disable');
            modal.modal('hide');
            $("table").fadeTo(100, 0.5);

            var resume = function() {
              $(".draggable").draggable('enable');
              refresh.resume();
            }

            $.ajax({
              url:    "/move_region",
              method: "PUT",
              data: {
                src:    src,
                dest:   dest,
                region: region
              },
              success: function(result) {
                debug("Succeeded to move " + region);
                resume();
                refreshApp(menu, opts);
              },
              error: function(jqXHR, text, error) {
                debug(jqXHR, text, error);
                resume();
                $("table").fadeTo(100, 1.0);
                title.html("Failed to move " + region);
                body.html($("<pre>", { text: jqXHR.responseText }));
                yes.hide();
                modal.on('shown.bs.modal', function() {
                  no.focus();
                }).modal();
              }
            });
          }).show();
          no.unbind('click');
          no.click(function(e) {
            refresh.resume();
          });
          modal.on('shown.bs.modal', function() {
            yes.focus();
          }).modal();
        }
      });
    },
    error: function(jqXHR, text, error) {
      debug(jqXHR, text, error);
      React.render(<App {...opts} menu="error" error={error}/>, document.body);
    },
    timeout: 10000
  });
}

var App = React.createClass({
  getDefaultProps: function() {
    return {
      menu: "rs"
    }
  },
  spinner: null,
  componentDidMount: function() {
    debug("app-mounted");
    if (this.spinner == null) {
      var target = document.getElementById("spinner");
      this.spinner = new Spinner({color:'#999', lines: 12}).spin(target);
    }
    refreshApp(this.props.menu, {});
  },
  componentDidUpdate: function(prevProps, prevState) {
    debug("app-updated");
    if (this.spinner != null) {
      this.spinner.stop();
    }
  },
  changeMenu: function(menu) {
    // TODO state = condensed
    refresh.version++;
    refreshApp(menu, {});
  },
  render: function() {
    debug(this.props.menu);
    return (
      <div>
        <nav className="navbar navbar-default" role="navigation">
          <div className="container">
            <div className="navbar-header">
              <a className="navbar-brand" href="/">
                <span className="glyphicon glyphicon-align-left" aria-hidden="true"></span>
              </a>
              <a className="navbar-brand" href="javascript:void(0)" onClick={this.changeMenu.bind(this, "rs")}>
                hbase-region-inspector
              </a>
            </div>
            <div className="collapse navbar-collapse">
              <ul className="nav navbar-nav">
                <li className={this.props.menu == "rs" ? "active" : ""}>
                  <a href="javascript:void(0)" onClick={this.changeMenu.bind(this, "rs")}>Region servers</a>
                </li>
                <li className={this.props.menu == "tb" ? "active" : ""}>
                  <a href="javascript:void(0)" onClick={this.changeMenu.bind(this, "tb")}>Tables</a>
                </li>
              </ul>

              <ul className="nav navbar-nav navbar-right">
                <li className="navbar-text">
                  {zookeeper}
                </li>
              </ul>
            </div>
          </div>
        </nav>
        <div className="container">
          {this.props.menu == "error" ? (
            <div className="alert alert-danger" role="alert">
              <h5>
                <span className="label label-danger">{this.props.error.toUpperCase()}</span> Failed to collect data from server
              </h5>
            </div>
          ) : this.props.menu == "rs" ? <RegionByServer {...this.props}/> : <RegionByTable {...this.props}/>}
        </div>
        <div id="modal" className="modal">
          <div className="modal-dialog">
            <div className="modal-content">
              <div className="modal-header">
                <button type="button" className="close" data-dismiss="modal" aria-label="Close"><span aria-hidden="true">&times;</span></button>
                <h4 className="modal-title">Move region</h4>
              </div>
              <div className="modal-body">
                <p id="modal_body">
                </p>
              </div>
              <div className="modal-footer">
                <button type="button" className="btn btn-default" data-dismiss="modal">Close</button>
                <button type="button" className="btn btn-primary">Move</button>
              </div>
            </div>
          </div>
        </div>
      </div>
    );
  }
});

var RegionByServer = React.createClass({
  getDefaultProps: function() {
    return {
      tables: [],
      sort:   "metric",
      metric: "store-file-size-mb",
      result: null
    }
  },
  getInitialState: function() {
    return {
      condensed: false
    }
  },
  componentDidUpdate: function(prevProps, prevState) {
    debug("did-update")
    this.componentDidMount();
  },
  componentDidMount: function() {
    debug("did-mount")
    enablePopover("top");
    $("table").fadeTo(100, 1.0);

    // Schedule next update
    schedule(function() {
      debug("refresh server-regions");
      this.refresh({}, true);
    }.bind(this));
  },
  setMetric: function(val) {
    this.refresh({ metric: val });
  },
  setSort: function(val) {
    this.refresh({ sort: val });
  },
  setLayout: function(val) {
    this.setState({condensed: val});
  },
  setTable: function(val) {
    this.refresh({ tables: [val] });
  },
  toggleTable: function(val, visible) {
    var tables = _.without(this.props.tables, val)
    if (visible) {
      tables.push(val)
    }
    this.refresh({ tables: tables });
  },
  clearTable: function() {
    this.refresh({ tables: [] });
  },
  refresh: function(opts, nofade) {
    if (!nofade) $("table").fadeTo(100, 0.5);
    refreshApp("rs", _.extend(_.omit(this.props, "result"), opts))
  },
  render: function() {
    if (this.props.result == null) {
      return <div id="spinner"/>;
    }
    debug(this.props);
    var servers = this.props.result.servers;
    var error = this.props.result.error;
    var tables = this.props.result.tables;
    var sum = servers.reduce(function(sum, server) { return sum + server.sum }, 0);
    return (
      <div>
        <MetricsTab metric={this.props.metric} parent={this} callback={this.setMetric}/>
        <form className="form-horizontal">
          <div className="form-group">
            <label className="control-label col-xs-1">Sort</label>
            <div className="col-xs-11">
              <label className="radio-inline">
                <input type="radio" name="sortOptions" value="metric" defaultChecked={this.props.sort == "metric"} onChange={this.setSort.bind(this, "metric")}>Region</input>
              </label>
              <label className="radio-inline">
                <input type="radio" name="sortOptions" value="table" defaultChecked={this.props.sort == "table"} onChange={this.setSort.bind(this, "table")}>Table</input>
              </label>
            </div>
          </div>

          <div className="form-group">
            <label className="control-label col-xs-1">Layout</label>
            <div className="col-xs-11">
              <label className="radio-inline">
                <input type="radio" name="layoutOptions" value="normal" defaultChecked={!this.state.condensed} onChange={this.setLayout.bind(this, false)}>Normal</input>
              </label>
              <label className="radio-inline">
                <input type="radio" name="layoutOptions" value="condensed" defaultChecked={this.state.condensed} onChange={this.setLayout.bind(this, true)}>Condensed</input>
              </label>
            </div>
          </div>

          <div className="form-group">
            <label className="control-label col-xs-1">Tables</label>
            <div className="col-xs-11">
              <h5>
                {tables.map(function(name) {
                  var allVisible = this.props.tables.length == 0;
                  var visible = (allVisible || this.props.tables.indexOf(name) >= 0);
                  var bg = visible ? colorFor(name)[0] : "silver";
                  return (
                    <span key={name}
                          style={{backgroundColor: bg}}
                          onClick={this.toggleTable.bind(this, name, allVisible ? true : !visible)}
                          className="label label-info label-table">{name}</span>
                  )
                }, this)}
                <button type="button" className={"btn btn-default btn-xs" + (this.props.tables.length == 0 ? " hide" : "")} onClick={this.clearTable}>
                  <span className="glyphicon glyphicon-remove" aria-hidden="true"></span>
                </button>
              </h5>
            </div>
          </div>
        </form>

        {servers.length > 0 ? "" :
          <div className="alert alert-warning" role="alert">No data found</div>
        }
        <table className="table table-condensed barchart">
          <thead>
            <td></td>
            <td className="pull-right">
              <span className="metric-value">
                {servers.length > 0 ? ("Max: " + fmt(servers[0].max) + " / Total: " + fmt(sum)) : ""}
              </span>
            </td>
          </thead>
          <tbody>
          {servers.map(function(server) {
            return <RegionByServer.Row key={server.name} metric={this.props.metric} {...this.state} parent={this} callback={this.setTable} {...server} />
          }, this)}
          </tbody>
        </table>
      </div>
    )
  }
});

var MetricsTab = React.createClass({
  render: function() {
    return (
      <div className="row bottom-buffer">
        <div className="col-md-12">
          <ul className="nav nav-tabs">
            {[["store-file-size-mb",  "Data size"],
              ["requests",            "Requests"],
              ["requests-rate",       "Requests/sec"],
              ["write-requests",      "Writes"],
              ["write-requests-rate", "Writes/sec"],
              ["read-requests",       "Reads"],
              ["read-requests-rate",  "Reads/sec"],
              ["stores",              "Storefiles"],
              ["memstore-size-mb",    "Memstore"]].map(function(pair) {
                return (
                  <li key={"rs-tab-metric-" + pair[0]} role="presentation" className={pair[0] == this.props.metric ? "active" : ""}>
                    <a href="javascript:void(0)" onClick={this.props.callback.bind(this.props.parent, pair[0])}>{pair[1]}</a>
                  </li>
                );
            }, this)}
            <li className="pull-right disabled">
              <a id="refresh_msg" href="javascript:void(0)">
              </a>
            </li>
          </ul>
        </div>
      </div>
    );
  }
});

RegionByServer.Row = React.createClass({
  render: function() {
    var metric = this.props.metric;
    var regions = this.props.regions;
    var shortName = this.props.name.replace(/\..*/, '');
    var url = "http://" + this.props.name.replace(/,.*/, '') + ":60030";
    var localSum = this.props.sum;
    var condensed = this.props.condensed ? " condensed" : ""

    return (
      <tr className={condensed}>
        <td className="text-muted col-xs-1 nowrap">
          <a target="_blank" href={url}>
            <div className="mono-space">{shortName}</div>
          </a>
        </td>
        <td>
          <div className="progress droppable" data-server={this.props.name}>
            {regions.map(function(r) {
              var width = (this.props.max == 0 || localSum == 0) ? 0 :
                100 * r[metric] / this.props.max;
              return width <= 0 ? "" : (
                <div className="progress-bar extra-info draggable"
                     data-region={r['encoded-name']}
                     key={r['encoded-name']}
                     style={{width: width + '%',
                             color: colorFor(r.table)[1],
                             backgroundColor: colorFor(r.table)[0],
                             borderRight: '1px solid ' + colorFor(r.table)[1]}}
                     data-content={r.html}
                     onClick={this.props.callback.bind(this.props.parent, r.table)}>
                  {(!condensed && width > 2) ? r.table.split(":").pop()[0] : ''}
                </div>
              )
            }, this)}
            <div className="metric-value" style={{display: "inline-block"}}>
              {fmt(localSum)}
            </div>
          </div>
        </td>
      </tr>
    )
  }
});

var RegionByTable = React.createClass({
  getInitialState: function() {
    return {
      condensed: false
    }
  },
  getDefaultProps: function() {
    return {
      metric: "store-file-size-mb",
    }
  },
  componentDidUpdate: function(prevProps, prevState) {
    debug("did-update")
    this.componentDidMount();
  },
  componentDidMount: function() {
    debug("did-mount")
    enablePopover("right");
    $("table").fadeTo(100, 1.0);
    // Schedule next update
    schedule(function() {
      debug("refresh table-regions");
      refreshApp("tb", { metric: this.props.metric });
    }.bind(this));
  },
  setMetric: function(val) {
    $("table").fadeTo(100, 0.5);
    refreshApp("tb", { metric: val });
  },
  setLayout: function(val) {
    this.setState({condensed: val});
  },
  render: function() {
    if (this.props.result == null) {
      return <div id="spinner"/>;
    }
    return (
      <div>
        <MetricsTab metric={this.props.metric} parent={this} callback={this.setMetric}/>
        <form className="form-horizontal">
          <div className="form-group">
            <label className="control-label col-xs-1">Layout</label>
            <div className="col-xs-11">
              <label className="radio-inline">
                <input type="radio" name="layoutOptions" value="normal" defaultChecked={!this.state.condensed} onChange={this.setLayout.bind(this, false)}>Normal</input>
              </label>
              <label className="radio-inline">
                <input type="radio" name="layoutOptions" value="condensed" defaultChecked={this.state.condensed} onChange={this.setLayout.bind(this, true)}>Condensed</input>
              </label>
            </div>
          </div>
        </form>
        {this.props.result.length > 0 ? "" :
          <div className="alert alert-warning" role="alert">No data found</div>
        }
        {this.props.result.map(function(table) {
          return <RegionByTable.Row key={table.name} sum={table.sum} metric={this.props.metric}
                                    condensed={this.state.condensed} name={table.name} regions={table.regions}/>
        }, this)}
      </div>
    )
  }
})

RegionByTable.Row = React.createClass({
  render: function() {
    var metric = this.props.metric;
    var max = this.props.regions.reduce(function(prev, curr) {
      return curr[metric] > prev ? curr[metric] : prev
    }, 0);
    var condensed = this.props.condensed ? " condensed" : ""
    return (
      <div className="row">
        <div className="col-xs-12">
          <h4>{this.props.name} <small>{this.props.sum}</small></h4>
          <table className="table table-condensed barchart">
            <tbody>
            {this.props.regions.map(function(r) {
              var width = max == 0 ? 0 : 100 * r[this.props.metric] / max;
              var val = r[this.props.metric];
              return (
                <tr key={r['encoded-name']} className={condensed}>
                  <td className="text-muted col-xs-1">
                    <div data-content={r.html} className="mono-space extra-info">
                      {r['encoded-name']}
                    </div>
                  </td>
                  <td>
                    <div className="progress">
                      <div className="progress-bar"
                           style={{width: width + '%',
                                   color: colorFor(r.table)[1],
                                   backgroundColor: colorFor(r.table)[0]}}>
                        {(!condensed && width > 2) ? fmt(val) : ""}
                      </div>
                    </div>
                  </td>
                </tr>
              )
            }, this)}
            </tbody>
          </table>
        </div>
      </div>
    )
  }
});

$(document).ready(function() {
  React.render(<App/>, document.body);
})
