var refreshInterval = 10000;
var refreshTimeout = null;

function debug() {
  // console.log.apply(console, arguments);
}

function disablePopover() {
  $(".extra-info").popover('disable')
}

function enablePopover() {
  $(".extra-info").popover({
    trigger:   "hover",
    html:      true,
    placement: "right",
    container: "body"
  });
  $(".extra-info").popover('enable')
}

function refreshApp(menu, opts) {
  clearTimeout(refreshTimeout);

  var url = menu == "rs" ? "/server_regions.json" : "/table_regions.json"
  debug(opts)
  $.ajax({
    url: url,
    data: opts,
    success: function(result) {
      React.render(<App {...opts} menu={menu} result={result}/>, document.body);
      $(".draggable").draggable({
        helper: 'clone',
        revert: 'invalid',
        revertDuration: 200,
        start: function(e, ui) {
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
          $(e.target).show();
          enablePopover();
        }
      });
      $(".droppable").droppable({
        hoverClass: "drop-target",
        drop: function(e, ui) {
          var dest   = $(e.target).data("server");
          var src    = ui.draggable.parent().data("server");
          var region = ui.draggable.data("region")
          var modal  = $("#modal");
          var yes    = modal.find(".btn-primary");
          var no     = modal.find(".btn-primary");
          var title  = modal.find(".modal-title");
          var body   = modal.find(".modal-body");
          if (src != dest) {
            title.html("Move " + region);
            body.html(
              $("<ul>").append($("<li>", { text: "from " + src }))
                       .append($("<li>", { text: "to " + dest })));
            yes.unbind('click');
            yes.click(function(e) {
              $(".draggable").draggable('disable');
              modal.modal('hide');
              $("table").fadeTo(100, 0.5);
              $.ajax({
                url:    "/move_region",
                method: "PUT",
                data: {
                  src:    src,
                  dest:   dest,
                  region: region
                },
                success: function(result) {
                  debug("Succeeded to move ");
                  $(".draggable").draggable('enable');
                  refreshApp(menu, opts);
                },
                error: function(jqXHR, text, error) {
                  debug(jqXHR, text, error);
                  $(".draggable").draggable('enable');
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
            modal.on('shown.bs.modal', function() {
              yes.focus();
            }).modal();
          }
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
  componentDidMount: function() {
    debug("app-mounted");
    refreshApp(this.props.menu, {});
  },
  changeMenu: function(menu) {
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
              <a className="navbar-brand" href="/">
                hbase-region-inspector
              </a>
            </div>
            <div className="collapse navbar-collapse">
              <ul className="nav navbar-nav">
                <li className={this.props.menu == "rs" ? "active" : ""}>
                  <a href="javascript:void(0)" onClick={this.changeMenu.bind(this, "rs")}>Region servers</a>
                </li>
                <li className={this.props.menu == "table" ? "active" : ""}>
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
      table:  "",
      sort:   "metric",
      metric: "store-file-size-mb",
      result: null
    }
  },
  componentDidUpdate: function(prevProps, prevState) {
    debug("did-update")
    this.componentDidMount();
  },
  componentDidMount: function() {
    debug("did-mount")
    enablePopover();
    $("table").fadeTo(100, 1.0);

    // Schedule next update
    refreshTimeout = setTimeout(function() {
      debug("refresh server-regions");
      this.refresh({});
    }.bind(this), refreshInterval);
  },
  setMetric: function(val) {
    $("table").fadeTo(100, 0.5);
    this.refresh({ metric: val });
  },
  setSort: function(val) {
    $("table").fadeTo(100, 0.5);
    this.refresh({ sort: val });
  },
  setTable: function(val) {
    $("#rs_table_name").val(val);
    $("table").fadeTo(100, 0.5);
    this.refresh({ table: val });
  },
  setTableDelayed: function(e) {
    var table = e.target.value;
    clearTimeout(refreshTimeout);
    refreshTimeout = setTimeout(function() {
      $("table").fadeTo(100, 0.5);
      this.refresh({ table: table });
    }.bind(this), 750)
  },
  refresh: function(opts) {
    refreshApp("rs", _.extend(_.omit(this.props, "result"), opts))
  },
  render: function() {
    if (this.props.result == null) {
      return (
        <p className="text-left">
          <img src="images/spinner.gif" width="100"/>
        </p>
      );
    }
    debug(this.props);
    var servers = this.props.result.servers;
    var error = this.props.result.error;
    return (
      <div>
        <MetricsTab metric={this.props.metric} parent={this} callback={this.setMetric}/>
        <div className="row bottom-buffer">
          <div className="col-md-5">
            <div className={"input-group input-group-sm " + (error ? "has-error" : "")} role="group">
              <input id="rs_table_name" type="text" className="form-control" placeholder="Table name" onChange={this.setTableDelayed}/>
              <span className="input-group-btn">
                <button type="button" className="btn btn-default" onClick={this.setTable.bind(this, "")}>
                  <span className="glyphicon glyphicon-remove" aria-hidden="true"></span>
                </button>
                <button type="button" className={"btn btn-" + (this.props.sort == "metric" ? "primary" : "default")} onClick={this.setSort.bind(this, "metric")}>Sort by metric</button>
                <button type="button" className={"btn btn-" + (this.props.sort == "table" ? "primary" : "default")} onClick={this.setSort.bind(this, "table")}>Sort by table</button>
              </span>
            </div>
          </div>
        </div>

        {servers.length > 0 ? "" :
          <div className="alert alert-warning" role="alert">No servers found</div>
        }
        <table className="table table-condensed barchart">
          <tbody>
          {servers.map(function(server) {
            return <RegionByServer.Row key={server.name} metric={this.props.metric} parent={this} callback={this.setTable} {...server} />
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
              ["memstore-size-mb",    "Memstore"]].map(function(pair) {
                return (
                  <li key={"rs-tab-metric-" + pair[0]} role="presentation" className={pair[0] == this.props.metric ? "active" : ""}>
                    <a href="javascript:void(0)" onClick={this.props.callback.bind(this.props.parent, pair[0])}>{pair[1]}</a>
                  </li>
                );
            }, this)}
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

    return (
      <tr>
        <td className="text-muted col-xs-2">
          <a target="_blank" href={url}>
            {shortName}
          </a>
        </td>
        <td>
          <div className="progress droppable" data-server={this.props.name}>
            {regions.map(function(r) {
              var width = this.props.max == 0 ? 0 :
                100 * r[metric] / localSum * this.props.sum / this.props.max;
              return width <= 0 ? "" : (
                <div className="progress-bar extra-info draggable"
                     data-region={r['encoded-name']}
                     key={r['encoded-name']}
                     style={{width: width + '%',
                             color: r.color[1],
                             backgroundColor: r.color[0],
                             borderRight: '1px solid ' + r.color[1]}}
                     data-content={r.html}
                     onClick={this.props.callback.bind(this.props.parent, r.table)}>
                  {width > 2 ? r.table[0] : ''}
                </div>
              )
            }, this)}
          </div>
        </td>
      </tr>
    )
  }
});


var RegionByTable = React.createClass({
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
    enablePopover();
    $("table").fadeTo(100, 1.0);
    // Schedule next update
    refreshTimeout = setTimeout(function() {
      debug("refresh table-regions");
      refreshApp("tb", { metric: this.props.metric });
    }.bind(this), refreshInterval);
  },
  setMetric: function(val) {
    $("table").fadeTo(100, 0.5);
    refreshApp("tb", { metric: val });
  },
  render: function() {
    if (this.props.result == null) {
      return (
        <p className="text-left">
          <img src="images/spinner.gif" width="100"/>
        </p>
      );
    }
    return (
      <div>
        <MetricsTab metric={this.props.metric} parent={this} callback={this.setMetric}/>
        {this.props.result.length > 0 ? "" :
          <div className="alert alert-warning" role="alert">No tables found</div>
        }
        {this.props.result.map(function(table) {
          return <RegionByTable.Row key={table.name} sum={table.sum} metric={this.props.metric}
                                    name={table.name} regions={table.regions}/>
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
    return (
      <div className="row">
        <div className="col-xs-12">
          <h4>{this.props.name} <small>{this.props.sum}</small></h4>
          <table className="table table-condensed barchart">
            <tbody>
            {this.props.regions.map(function(r) {
              var width = max == 0 ? 0 : 100 * r[this.props.metric] / max;
              var val = r[this.props.metric];
              return width <= 0 ? "" : (
                <tr key={r['encoded-name']}>
                  <td className="text-muted mono-space col-xs-3">
                    <span data-content={r.html} className="extra-info">
                      {r['encoded-name']}
                    </span>
                  </td>
                  <td>
                    <div className="progress">
                      <div className="progress-bar"
                           style={{width: width + '%',
                                   color: r.color[1],
                                   backgroundColor: r.color[0]}}>
                        {val > 10 ? val : val.toFixed(2)}
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
