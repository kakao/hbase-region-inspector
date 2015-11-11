var _admin, _condensed, _interval, _rs_port, _tables, _zookeeper
var _, $, React, ReactDOM, Spinner

var refresh = {
  version: 0,
  interval: _interval,
  ticks: 0,
  paused: false,
  pause: function () {
    this.paused = true
  },
  resume: function () {
    this.paused = false
  },
  redraw: function () {
    var sec = refresh.interval - refresh.ticks
    $('.refresh_msg').text('Refresh in ' + sec + ' second' + (sec > 1 ? 's' : ''))
  },
  job: null,
  timeout: null
}

var colors = {}

function colorFor (table) {
  if (!colors[table]) {
    var h = Math.random() * 360
    var s = 30 + Math.random() * 20
    var l = 60 + Math.random() * 20
    colors[table] = [$.husl.toHex(h, s, l), $.husl.toHex(h, s, l - 10)]
  }
  return colors[table]
}

function schedule (job) {
  clearTimeout(refresh.timeout)
  refresh.job = job
  refresh.ticks = 0
  var tick = function () {
    refresh.redraw()
    if (refresh.ticks === refresh.interval) {
      job()
    } else {
      if (!refresh.paused) {
        refresh.ticks++
      }
      refresh.timeout = setTimeout(tick, 1000)
    }
  }
  tick()
}

function fire () {
  refresh.job()
}

function toggle_refresh () {
  var t = $('.resume-btn span')
  if (refresh.paused) {
    refresh.resume()
    t.removeClass('glyphicon-play').addClass('glyphicon-pause')
  } else {
    refresh.pause()
    t.removeClass('glyphicon-pause').addClass('glyphicon-play')
  }
}

function fmt (val) {
  if (val == null) {
    return ''
  }
  if (val > 10 || Math.floor(val) === val) {
    return Math.floor(val).toString().replace(/\B(?=(\d{3})+(?!\d))/g, ',')
  }
  return val.toFixed(2)
}

function ratio (a, b) {
  return (b == null || b === 0) ? '' : fmt(1.0 * a / b)
}

function summarize (arr, keys) {
  var sum = {sum: 0, max: 0}
  _.each(keys, function (k) {
    sum[k] = null
  })
  _.each(arr, function (e) {
    _.each(keys, function (k) {
      if (e.props[k] != null) {
        sum[k] += e.props[k]
      }
    })
    sum.sum += e.sum
    sum.max = e.sum > sum.max ? e.sum : sum.max
  })
  return sum
}

function debug () {
  // console.log.apply(console, arguments)
}

function disablePopover () {
  ['.extra-info', '.extra-info-right', '.extra-info-bottom'].map(function (klass) {
    $(klass).popover('disable')
  })
}

function enablePopover () {
  var enable = function (klass, pos) {
    $(klass).popover({
      trigger: 'hover',
      html: true,
      placement: pos,
      container: 'body'
    })
    $(klass).popover('enable')
  }
  enable('.extra-info', 'top')
  enable('.extra-info-right', 'right')
  enable('.extra-info-bottom', 'bottom')
}

function startDrag () {
  $('.draggable').draggable({
    helper: 'clone',
    revertDuration: 200,
    start: function (e, ui) {
      refresh.pause()

      var orig = $(e.target)
      disablePopover()
      ui.helper.width(orig.width()).height(orig.height()).css({
        'border-width': '2px',
        'border-color': orig.css('color'),
        'border-style': 'solid',
        '-webkit-transition': 'none',
        '-moz-transition': 'none',
        '-ms-transition': 'none',
        '-o-transition': 'none',
        'transition': 'none'
      })
      orig.hide()
    },
    stop: function (e, ui) {
      debug('Drag stopped')
      $(e.target).show()
      enablePopover()
    },
    revert: function (valid) {
      if (!valid) {
        refresh.resume()
      }
    }
  })
}

function startDrop (callback) {
  $('.droppable').droppable({
    hoverClass: 'drop-target',
    drop: function (e, ui) {
      var src = ui.draggable.parent().data('server')
      var dest = $(e.target).data('server')

      debug('Dropped ', src, dest)
      if (src === dest) {
        return
      }

      var region = ui.draggable.data('region')
      var modal = $('#modal')
      var yes = modal.find('.btn-primary')
      var no = modal.find('.btn-default')
      var title = modal.find('.modal-title')
      var body = modal.find('.modal-body')

      title.html('Move ' + region)
      body.html(
        $('<ul>').append($('<li>', { text: 'from ' + src }))
        .append($('<li>', { text: 'to ' + dest })))
      yes.unbind('click')
      yes.click(function (e) {
        $('.draggable').draggable('disable')
        modal.modal('hide')
        $('table').fadeTo(100, 0.5)

        var resume = function () {
          $('.draggable').draggable('enable')
          refresh.resume()
        }

        $.ajax({
          url: '/move_region',
          method: 'PUT',
          data: {
            src: src,
            dest: dest,
            region: region
          },
          success: function (result) {
            debug('Succeeded to move ' + region)
            resume()
            callback()
          },
          error: function (jqXHR, text, error) {
            debug(jqXHR, text, error)
            resume()
            $('table').fadeTo(100, 1.0)
            title.html('Failed to move ' + region)
            body.html($('<pre>', { text: jqXHR.responseText }))
            yes.hide()
            modal.on('shown.bs.modal', function () {
              no.focus()
            }).modal()
          }
        })
      }).show()
      no.unbind('click')
      no.click(function (e) {
        refresh.resume()
      })
      modal.on('shown.bs.modal', function () {
        yes.focus()
      }).modal()
    }
  })
}

function refreshApp (menu, opts) {
  clearTimeout(refresh.timeout)
  refresh.version++

  var url = menu === 'servers' ? '/server_regions.json' : '/table_regions.json'
  var currentVersion = refresh.version
  debug(opts)
  $.ajax({
    url: url,
    data: opts,
    success: function (result) {
      if (refresh.version !== currentVersion) {
        debug('already updated: ' + currentVersion + '/' + refresh.version)
        return
      }
      ReactDOM.render(<App {...opts} menu={menu} result={result}/>, document.getElementById('content'))
      if (_admin) {
        startDrag()
        startDrop(function () { refreshApp(menu, opts) })
      }
      if (!_locality) $(".locality").hide()
    },
    error: function (jqXHR, text, error) {
      debug(jqXHR, text, error)
      ReactDOM.render(<App {...opts} menu='error' error={error}/>, document.getElementById('content'))
      schedule(function () { refreshApp(menu, opts) })
    },
    timeout: 10000
  })
}

function parseHash () {
  var ret = {menu: 'servers', sort: 'metric', metric: 'store-file-size-mb'}
  var vals = window.location.hash.split('#')
  if (vals.length > 1) {
    ret.menu = vals[1]
    ret.sort = vals[1] === 'regions' ? 'start-key' : 'metric'
  }
  if (vals.length > 2) {
    ret.metric = vals[2]
  }
  return ret
}

var App = React.createClass({
  propTypes: {
    menu: React.PropTypes.string.isRequired,
    sort: React.PropTypes.string.isRequired,
    metric: React.PropTypes.string.isRequired,
    error: React.PropTypes.string
  },
  getDefaultProps: function () {
    var opts = parseHash()
    return {
      menu: opts.menu,
      sort: opts.sort,
      metric: opts.metric
    }
  },
  spinner: null,
  componentDidMount: function () {
    debug('app-mounted')
    if (this.spinner == null) {
      var target = document.getElementById('spinner')
      this.spinner = new Spinner({color: '#999', lines: 12}).spin(target)
    }
    refreshApp(this.props.menu, {sort: this.props.sort, metric: this.props.metric})
  },
  componentDidUpdate: function (prevProps, prevState) {
    debug('app-updated')
    if (this.spinner != null) {
      this.spinner.stop()
    }
    enablePopover()
  },
  changeMenu: function () {
    var vals = window.location.hash.split('#')
    var menu = vals[1]
    var opts = {
      sort: this.props.menu === menu
        ? this.props.sort
        : (menu === 'regions' ? 'start-key' : 'metric'),
      metric: this.props.metric,
      tables: _tables
    }
    if (vals.length > 2) {
      opts.metric = vals[2]
    }
    refreshApp(menu, opts)
  },
  render: function () {
    debug(this.props.menu)
    return (
      <div>
        <nav className='navbar navbar-default' role='navigation'>
          <div className='container'>
            <div className='navbar-header'>
              <a className='navbar-brand' href='/'>
                <span className='glyphicon glyphicon-align-left' aria-hidden='true'></span>
              </a>
              <a className='navbar-brand' href='#servers'>
                hbase-region-inspector
              </a>
            </div>
            <div className='collapse navbar-collapse'>
              <ul className='nav navbar-nav'>
                <li className={this.props.menu === 'servers' ? 'active' : ''}>
                  <a href='#servers'>Servers</a>
                </li>
                <li className={this.props.menu === 'tables' ? 'active' : ''}>
                  <a href='#tables'>Tables</a>
                </li>
                <li className={this.props.menu === 'regions' ? 'active' : ''}>
                  <a href='#regions'>Regions</a>
                </li>
              </ul>

              <ul className='nav navbar-nav navbar-right'>
                <li className='navbar-text'>
                  {_zookeeper}
                </li>
              </ul>
            </div>
          </div>
        </nav>
        <div className='container'>
          {this.props.menu === 'error' ? (
            <div className='alert alert-danger' role='alert'>
              <h5>
                <span className='label label-danger'>
                  {this.props.error.toUpperCase()}
                </span> Failed to collect data from server (<span className='refresh_msg'></span>)
              </h5>
            </div>
          ) : this.props.menu === 'servers'
              ? <RegionByServer {...this.props}/>
              : <RegionByTable sort={this.props.menu === 'regions' ? 'start-key' : ''} {...this.props}/>}
        </div>
        <div id='modal' className='modal'>
          <div className='modal-dialog'>
            <div className='modal-content'>
              <div className='modal-header'>
                <button type='button' className='close' data-dismiss='modal' aria-label='Close'><span aria-hidden='true'>&times;</span></button>
                <h4 className='modal-title'>Move region</h4>
              </div>
              <div className='modal-body'>
                <p id='modal_body'>
                </p>
              </div>
              <div className='modal-footer'>
                <button type='button' className='btn btn-default' data-dismiss='modal'>Close</button>
                <button type='button' className='btn btn-primary'>Move</button>
              </div>
            </div>
          </div>
        </div>
      </div>
    )
  }
})

var tableSelectable = {
  toggleTable: function (val, visible) {
    var tables = _.without(this.props.tables, val)
    if (visible) {
      tables.push(val)
    }
    _tables = tables
    this.refresh({ tables: tables })
  },
  clearTable: function () {
    _tables = []
    this.refresh({ tables: [] })
  }
}

var RegionByServer = React.createClass(_.extend({
  getDefaultProps: function () {
    var opts = parseHash()
    return {
      tables: [],
      sort: opts.sort,
      metric: opts.metric,
      menu: opts.menu,
      result: null
    }
  },
  getInitialState: function () {
    return {
      condensed: _condensed
    }
  },
  componentDidUpdate: function (prevProps, prevState) {
    debug('did-update')
    this.componentDidMount()
  },
  componentDidMount: function () {
    debug('did-mount')
    $('table').fadeTo(100, 1.0)

    // Schedule next update
    schedule(function () {
      debug('refresh server-regions')
      this.refresh({}, true)
    }.bind(this))
  },
  setSort: function (val) {
    this.refresh({ sort: val })
  },
  setLayout: function (val) {
    _condensed = val
    this.setState({condensed: val})
  },
  setTable: function (val) {
    _tables = [val]
    this.refresh({ tables: [val] })
  },
  refresh: function (opts, nofade) {
    if (!nofade) $('table').fadeTo(100, 0.5)
    refreshApp('servers', _.extend(_.omit(this.props, 'result'), opts))
  },
  render: function () {
    if (this.props.result == null) {
      return <div id='spinner'/>
    }
    debug(this.props)
    var servers = this.props.result.servers
    var sums = summarize(servers,
                         ['regions',
                          'store-files',
                          'store-file-size-mb',
                          'store-uncompressed-size-mb',
                          'local-size-mb',
                          'requests-rate',
                          'used-heap-mb',
                          'max-heap-mb'])
    var sum = sums.sum
    servers.reduce(function (sum, server) { return sum + server.sum }, 0)
    return (
      <div>
        <MetricsTab menu={this.props.menu} metric={this.props.metric} parent={this}/>
        <form className='form-horizontal'>
          <div className='form-group'>
            <label className='control-label col-xs-1'>Sort</label>
            <div className='col-xs-11'>
              <label className='radio-inline col-xs-1'>
                <input type='radio' name='sortOptions' value='metric' defaultChecked={this.props.sort === 'metric'} onChange={this.setSort.bind(this, 'metric')}/>Region
              </label>
              <label className='radio-inline'>
                <input type='radio' name='sortOptions' value='table' defaultChecked={this.props.sort === 'table'} onChange={this.setSort.bind(this, 'table')}/>Table
              </label>
            </div>
          </div>

          <div className='form-group'>
            <label className='control-label col-xs-1'>Layout</label>
            <div className='col-xs-11'>
              <label className='radio-inline col-xs-1'>
                <input type='radio' name='layoutOptions' value='normal' defaultChecked={!this.state.condensed} onChange={this.setLayout.bind(this, false)}/>Normal
              </label>
              <label className='radio-inline'>
                <input type='radio' name='layoutOptions' value='condensed' defaultChecked={this.state.condensed} onChange={this.setLayout.bind(this, true)}/>Condensed
              </label>
            </div>
          </div>

          <TableButtons allTables={this.props.result.tables} tables={this.props.tables} parent={this}/>
        </form>

        {servers.length > 0
          ? ''
          : <div className='alert alert-warning' role='alert'>No data found</div>
        }
        <table className='table table-condensed barchart'>
          <thead>
            <tr>
              <td></td>
              <td className='pull-right'>
                <span className='metric-value'>
                  {servers.length > 0 ? ('Max: ' + fmt(servers[0].max) + ' / Total: ' + fmt(sum)) : ''}
                </span>
              </td>
            </tr>
          </thead>
          <tbody>
          {servers.map(function (server, idx) {
            return <RegionByServer.Row key={server.name} index={idx} metric={this.props.metric} {...this.state} parent={this} callback={this.setTable} {...server} />
          }, this)}
          </tbody>
        </table>
        <hr/>
        <table className='table table-striped'>
          <thead>
            <tr>
              <th>Server</th>
              <th>Regions</th>
              <th>Storefiles</th>
              <th>Compressed (MB)</th>
              <th>Uncompressed (MB)</th>
              <th>(Ratio)</th>
              <th className="locality">Locality (%)</th>
              <th>Requests/sec</th>
              <th>Used heap (MB)</th>
              <th>Max heap (MB)</th>
            </tr>
          </thead>
          <tbody>
            {servers.map(function (server) {
              return (
                <tr key={'server-row-' + server.name}>
                  <th>{server.name.replace(/\..*/, '')}</th>
                  <td>{fmt(server.props['regions'])}</td>
                  <td>{fmt(server.props['store-files'])}</td>
                  <td>{fmt(server.props['store-file-size-mb'])}</td>
                  <td>{fmt(server.props['store-uncompressed-size-mb'])}</td>
                  <td>{ratio(server.props['store-uncompressed-size-mb'],
                             server.props['store-file-size-mb'])}</td>
                  <td className="locality">{fmt(server.props['locality'])}</td>
                  <td>{fmt(server.props['requests-rate'])}</td>
                  <td>{fmt(server.props['used-heap-mb'])}</td>
                  <td>{fmt(server.props['max-heap-mb'])}</td>
                </tr>
              )
            }, this)}
            <tr>
              <th><span className='mark'><em>ALL SERVERS ({servers.length})</em></span></th>
              <td><em>{fmt(sums['regions'])}</em></td>
              <td><em>{fmt(sums['store-files'])}</em></td>
              <td><em>{fmt(sums['store-file-size-mb'])}</em></td>
              <td><em>{fmt(sums['store-uncompressed-size-mb'])}</em></td>
              <td><em>{ratio(sums['store-uncompressed-size-mb'],
                             sums['store-file-size-mb'])}</em></td>
              <td className="locality">
                <em>{Math.floor(100 * sums['local-size-mb'] / sums['store-file-size-mb'])}</em>
              </td>
              <td><em>{fmt(sums['requests-rate'])}</em></td>
              <td><em>{fmt(sums['used-heap-mb'])}</em></td>
              <td><em>{fmt(sums['max-heap-mb'])}</em></td>
            </tr>
          </tbody>
        </table>
      </div>
    )
  }
}, tableSelectable))

var MetricsTab = React.createClass({
  propTypes: {
    metric: React.PropTypes.string.isRequired,
    menu: React.PropTypes.string.isRequired
  },
  render: function () {
    return (
      <div className='row bottom-buffer'>
        <div className='col-md-12'>
          <ul className='nav nav-tabs'>
            {[['store-file-size-mb',  'Data size'],
              ['requests',            'Requests'],
              ['requests-rate',       'Requests/sec'],
              ['write-requests',      'Writes'],
              ['write-requests-rate', 'Writes/sec'],
              ['read-requests',       'Reads'],
              ['read-requests-rate',  'Reads/sec'],
              ['store-files',         'Storefiles'],
              ['locality',            'Locality'],
              ['memstore-size-mb',    'Memstore']].map(function (pair) {
                // Locality information is only available on HBase 1.0 or above
                return (!_locality && pair[0] == 'locality') ? "" : (
                  <li key={'rs-tab-metric-' + pair[0]} role='presentation' className={pair[0] === this.props.metric ? 'active' : ''}>
                    <a href={'#' + this.props.menu + '#' + pair[0]}>{pair[1]}</a>
                  </li>
                )
              }, this)}
            <li className='navbar-right disabled'>
              <a className='resume-btn' href='javascript:void(0)' onClick={toggle_refresh}>
                <span className={'glyphicon glyphicon-' + (refresh.paused ? 'play' : 'pause')} aria-hidden='true'></span>
              </a>
            </li>
            <li className='navbar-right disabled'>
              <a className='refresh_msg' href='javascript:void(0)' onClick={fire}>
              </a>
            </li>
          </ul>
        </div>
      </div>
    )
  }
})

var TableButtons = React.createClass({
  propTypes: {
    allTables: React.PropTypes.array.isRequired,
    tables: React.PropTypes.array.isRequired,
    parent: React.PropTypes.object.isRequired
  },
  render: function () {
    var p = this.props.parent
    return (
      <div className='form-group'>
        <label className='control-label col-xs-1'>Tables</label>
        <div id='table-buttons' className='col-xs-11'>
          <h5>
            {this.props.allTables.map(function (name) {
              var allVisible = this.props.tables.length === 0
              var visible = (allVisible || this.props.tables.indexOf(name) >= 0)
              var bg = visible ? colorFor(name)[0] : 'silver'
              return (
                <span key={name}
                      style={{backgroundColor: bg}}
                      onClick={p.toggleTable.bind(p, name, allVisible ? true : !visible)}
                      className='label label-info label-table'>{name}</span>
              )
            }, this)}
            <button type='button' className={'btn btn-default btn-xs' + (this.props.tables.length === 0 ? ' hide' : '')} onClick={p.clearTable}>
              <span className='glyphicon glyphicon-remove' aria-hidden='true'></span>
            </button>
          </h5>
        </div>
      </div>
    )
  }
})

RegionByServer.Row = React.createClass({
  propTypes: {
    condensed: React.PropTypes.bool.isRequired,
    metric: React.PropTypes.string.isRequired,
    regions: React.PropTypes.array.isRequired,
    name: React.PropTypes.string.isRequired,
    sum: React.PropTypes.number.isRequired,
    max: React.PropTypes.number.isRequired,
    index: React.PropTypes.number.isRequired,
    props: React.PropTypes.object.isRequired,
    callback: React.PropTypes.func.isRequired,
    parent: React.PropTypes.object.isRequired
  },
  render: function () {
    var metric = this.props.metric
    var regions = this.props.regions
    var shortName = this.props.name.replace(/\..*/, '')
    var url = 'http://' + this.props.name.replace(/,.*/, '') + ':' + _rs_port
    var localSum = this.props.sum
    var condensed = this.props.condensed ? ' condensed' : ''
    var klass = 'progress-bar draggable extra-info' + (this.props.index > 2 ? '' : '-bottom')

    return (
      <tr className={condensed}>
        <td className='text-muted col-xs-1 nowrap'>
          <a target='_blank' href={url}>
            <div className='mono-space extra-info-right' data-content={this.props.props.html}>{shortName}</div>
          </a>
        </td>
        <td>
          <div className='progress droppable' data-server={this.props.name}>
            {regions.map(function (r) {
              var width = (this.props.max === 0 || localSum === 0)
                ? 0
                : 100 * r[metric] / this.props.max
              var base = (width < 0.2)
                ? { backgroundColor: colorFor(r.table)[1] }
                : { backgroundColor: colorFor(r.table)[0],
                    borderRight: '1px solid' + colorFor(r.table)[1] }

              return width <= 0 ? '' : (
                <div className={klass}
                     data-region={r['encoded-name']}
                     key={r['encoded-name']}
                     style={_.extend(base, {width: width + '%', color: colorFor(r.table)[1]})}
                     data-content={r.html}
                     onClick={this.props.callback.bind(this.props.parent, r.table)}>
                  {(!condensed && width > 2) ? r.table.split(':').pop()[0] : ''}
                </div>
              )
            }, this)}
            <div className='metric-value' style={{display: 'inline-block'}}>
              {fmt(localSum)}
            </div>
          </div>
        </td>
      </tr>
    )
  }
})

var RegionByTable = React.createClass(_.extend({
  getInitialState: function () {
    return {
      condensed: _condensed
    }
  },
  getDefaultProps: function () {
    var opts = parseHash()
    return {
      tables: [],
      metric: opts.metric,
      sort: opts.sort,
      menu: 'tables'
    }
  },
  componentDidUpdate: function (prevProps, prevState) {
    debug('did-update')
    this.componentDidMount()
  },
  componentDidMount: function () {
    debug('did-mount')
    $('table').fadeTo(100, 1.0)
    // Schedule next update
    schedule(function () {
      debug('refresh table-regions')
      this.refresh({}, true)
    }.bind(this))
  },
  setSort: function (val) {
    this.refresh({ sort: val })
  },
  refresh: function (opts, nofade) {
    if (!nofade) $('table').fadeTo(100, 0.5)
    refreshApp(this.props.menu, _.extend(_.omit(this.props, 'result'), opts))
  },
  setLayout: function (val) {
    _condensed = val
    this.setState({condensed: val})
  },
  render: function () {
    if (this.props.result == null) {
      return <div id='spinner'/>
    }
    var allTables = this.props.result['all-tables']
    var tables = this.props.result.tables
    return (
      <div>
        <MetricsTab menu={this.props.menu} metric={this.props.metric} parent={this}/>
        <form className='form-horizontal'>
          <div className='form-group'>
            <label className='control-label col-xs-1'>Sort</label>
            <div className='col-xs-11'>
              <label className='radio-inline col-xs-1'>
                <input type='radio' name='sortOptions' value='table' checked={this.props.sort === 'metric'} onChange={this.setSort.bind(this, 'metric')}/>Value
              </label>
              <label className='radio-inline'>
                <input type='radio' name='sortOptions' value='metric' checked={this.props.sort === 'start-key'} onChange={this.setSort.bind(this, 'start-key')}/>Start key
              </label>
            </div>
          </div>

          <div className='form-group'>
            <label className='control-label col-xs-1'>Layout</label>
            <div className='col-xs-11'>
              <label className='radio-inline col-xs-1'>
                <input type='radio' name='layoutOptions' value='normal' defaultChecked={!this.state.condensed} onChange={this.setLayout.bind(this, false)}/>Normal
              </label>
              <label className='radio-inline'>
                <input type='radio' name='layoutOptions' value='condensed' defaultChecked={this.state.condensed} onChange={this.setLayout.bind(this, true)}/>Condensed
              </label>
            </div>
          </div>

          <TableButtons allTables={allTables} tables={this.props.tables} parent={this}/>
        </form>
        {tables.length > 0
          ? ''
          : <div className='alert alert-warning' role='alert'>No data found</div>}
        {this.props.menu === 'regions'
          ? tables.map(function (table) {
            return <RegionByTable.Regions key={table.name} sum={table.sumh} metric={this.props.metric}
                                        condensed={this.state.condensed} name={table.name} regions={table.regions}/>
          }, this)
          : <RegionByTable.Table tables={tables} condensed={this.state.condensed} metric={this.props.metric}/>}
      </div>
    )
  }
}, tableSelectable))

RegionByTable.Table = React.createClass({
  propTypes: {
    tables: React.PropTypes.array.isRequired,
    metric: React.PropTypes.string.isRequired,
    condensed: React.PropTypes.bool.isRequired
  },
  render: function () {
    var tables = this.props.tables
    var metric = this.props.metric
    var sums = summarize(this.props.tables,
                         ['regions',
                          'store-files',
                          'store-file-size-mb',
                          'store-uncompressed-size-mb',
                          'local-size-mb',
                          'requests-rate',
                          'read-requests-rate',
                          'write-requests-rate'])
    var max = sums.max
    var sum = sums.sum
    return (
      <div>
      <table className='table table-condensed barchart'>
        <thead>
          <tr>
            <td></td>
            <td className='pull-right'>
              <span className='metric-value'>
                {tables.length > 0 ? ('Max: ' + fmt(max) + ' / Total: ' + fmt(sum)) : ''}
              </span>
            </td>
          </tr>
        </thead>
        <tbody>
          {tables.map(function (table, idx) {
            return <RegionByTable.TableRow key={table.name} index={idx} condensed={this.props.condensed} max={max} metric={metric} {...table} />
          }, this)}
        </tbody>
      </table>
      <hr/>
      <table className='table table-striped'>
        <thead>
          <tr>
            <th>Table</th>
            <th>Regions</th>
            <th>Storefiles</th>
            <th>Compressed (MB)</th>
            <th>Uncompressed (MB)</th>
            <th>(Ratio)</th>
            <th className="locality">Locality (%)</th>
            <th>Requests/sec</th>
            <th>Reads/sec</th>
            <th>Writes/sec</th>
          </tr>
        </thead>
        <tbody>
          {tables.map(function (table) {
            return (
              <tr key={'table-row-' + table.name}>
                <th>{table.name}</th>
                <td>{fmt(table.props['regions'])}</td>
                <td>{fmt(table.props['store-files'])}</td>
                <td>{fmt(table.props['store-file-size-mb'])}</td>
                <td>{fmt(table.props['store-uncompressed-size-mb'])}</td>
                <td>{ratio(table.props['store-uncompressed-size-mb'],
                           table.props['store-file-size-mb'])}</td>
                <td className="locality">{fmt(table.props['locality'])}</td>
                <td>{fmt(table.props['requests-rate'])}</td>
                <td>{fmt(table.props['read-requests-rate'])}</td>
                <td>{fmt(table.props['write-requests-rate'])}</td>
              </tr>
            )
          }, this)}
          <tr>
            <th><span className='mark'><em>ALL TABLES ({tables.length})</em></span></th>
            <td><em>{fmt(sums['regions'])}</em></td>
            <td><em>{fmt(sums['store-files'])}</em></td>
            <td><em>{fmt(sums['store-file-size-mb'])}</em></td>
            <td><em>{fmt(sums['store-uncompressed-size-mb'])}</em></td>
            <td><em>{ratio(sums['store-uncompressed-size-mb'],
                           sums['store-file-size-mb'])}</em></td>
            <td className="locality">
              <em>{Math.floor(100 * sums['local-size-mb'] / sums['store-file-size-mb'])}</em>
            </td>
            <td><em>{fmt(sums['requests-rate'])}</em></td>
            <td><em>{fmt(sums['read-requests-rate'])}</em></td>
            <td><em>{fmt(sums['write-requests-rate'])}</em></td>
          </tr>
        </tbody>
      </table>
      </div>
    )
  }
})

RegionByTable.TableRow = React.createClass({
  propTypes: {
    condensed: React.PropTypes.bool.isRequired,
    props: React.PropTypes.object.isRequired,
    name: React.PropTypes.string.isRequired,
    metric: React.PropTypes.string.isRequired,
    regions: React.PropTypes.array.isRequired,
    max: React.PropTypes.number.isRequired,
    sum: React.PropTypes.number.isRequired,
    index: React.PropTypes.number.isRequired
  },
  render: function () {
    return (
      <tr className={this.props.condensed ? 'condensed' : ''}>
        <td className='text-muted col-xs-1 nowrap'>
          <div className='mono-space extra-info-right' data-content={this.props.props.html}>
            {this.props.name}
          </div>
        </td>
        <td>
          <div className='progress'>
            {this.props.regions.map(function (r) {
              var width = (this.props.max === 0)
                ? 0
                : 100 * r[this.props.metric] / this.props.max
              var base = (width < 0.2)
                ? { backgroundColor: colorFor(r.table)[1] }
                : { backgroundColor: colorFor(r.table)[0],
                    borderRight: '1px solid' + colorFor(r.table)[1] }
              var klass = 'progress-bar extra-info' + (this.props.index > 2 ? '' : '-bottom')
              return width === 0 ? '' : (
                <div className={klass}
                     key={r['encoded-name']}
                     data-content={r.html}
                     style={_.extend(base, {width: width + '%'})}>
                </div>
              )
            }, this)}
            <div className='metric-value' style={{display: 'inline-block'}}>
              {fmt(this.props.sum)}
            </div>
          </div>
        </td>
      </tr>
    )
  }
})

RegionByTable.Regions = React.createClass({
  propTypes: {
    name: React.PropTypes.string.isRequired,
    metric: React.PropTypes.string.isRequired,
    regions: React.PropTypes.array.isRequired,
    sum: React.PropTypes.number.isRequired,
    condensed: React.PropTypes.bool.isRequired
  },
  render: function () {
    var metric = this.props.metric
    var max = this.props.regions.reduce(function (cmax, curr) {
      return curr[metric] > cmax ? curr[metric] : cmax
    }, 0)
    return (
      <div className='row table-regions'>
        <div className='col-xs-12'>
          <h4>{this.props.name} <small>{this.props.sum}</small></h4>
          <table className='table table-condensed barchart'>
            <tbody>
            {this.props.regions.map(function (r) {
              var width = max === 0 ? 0 : 100 * r[this.props.metric] / max
              var val = r[this.props.metric]
              return (
                <tr key={r['encoded-name']} className={this.props.condensed ? 'condensed' : ''}>
                  <td className='text-muted col-xs-1'>
                    <div data-content={r.html} className='mono-space extra-info-right'>
                      {r['encoded-name']}
                    </div>
                  </td>
                  <td>
                    <div className='progress'>
                      <div className='progress-bar'
                           style={{width: width + '%',
                                   color: colorFor(r.table)[1],
                                   backgroundColor: colorFor(r.table)[0]}}>
                        {(!this.props.condensed && width > 2) ? fmt(val) : ''}
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
})

$(document).ready(function () {
  var app = ReactDOM.render(<App/>, document.getElementById('content'))
  window.addEventListener('hashchange', function () {
    $('table').fadeTo(100, 0.5)
    app.changeMenu()
  })
})
