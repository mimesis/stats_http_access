(function(){
	function isBlank(str) {
		return (!str || /^\s*$/.test(str));
	}

	function selectedInterval() {
		var str = "";
		$("#select_interval_month option:selected").each(function () {
			str += $(this).val();
		});
		$("#select_interval_dayOfMonth option:selected").each(function () {
			var v = $(this).val();
			if (!isBlank(v)) { str += "/" +v ;}
		});
		return str;
	}

	function selectedMetric() {
		var str = "";
		$("#select_metric option:selected").each(function () {
			str += $(this).val();
		});
		return str;
		return "http_duration";
	}

	function updateData(interval, metric, oTable) {
		$.ajax({
			type : "GET",
			cache : false,
			dataType : "json",
			url : "/tmp/stats_http/" + interval + "/" + metric + ".json",
			success : function(response) {
				if (response.statistics) {
					oTable.fnClearTable(false);
					oTable.fnAddData(response.statistics, true);
				} else {
					oTable.fnClearTable(true);
				}
				$('#tableTitle').text(response.metric + "("+ response.unit +")" + " : " + response.interval);
			},
			statusCode: {
				404: function() {
					oTable.fnClearTable(true);
					alert('data not found');
				}
			}
		});
	}

	var oTable = $('#example').dataTable({
		"bProcessing" : true,
		"bDeferRender" : true,
		"aoColumns" : [
		               { sWidth: '13em' },
		               { sWidth: '13em' },
		               { },
		               { sWidth: '2em', sClass: "alignRight" },
		               { sWidth: '2em', sClass: "alignRight" },
		               { sWidth: '2em', sClass: "alignRight" },
		               { sWidth: '2em', sClass: "alignRight" },
		               { sWidth: '2em', sClass: "alignRight" }
		               ]
	});

	var today = new Date();
	var today_yyyy = 1900 + today.getYear();
	var today_mm = 1 + today.getMonth();
	var yearMonth = $('#select_interval_month');
	for (yyyy = today_yyyy ; yyyy >= 2011; yyyy--) {
		for (mm = 12 ; mm >= 1; mm--) {
			if (!(yyyy == today_yyyy && mm > today_mm)) {
				var mm0 = ((mm < 10) ? "0" : "") + mm
				yearMonth.append( new Option(yyyy + "-" + mm0 , yyyy+mm0) );
			}
		};
	};

	var dayOfMonth = $('#select_interval_dayOfMonth');
	dayOfMonth.append( new Option("aggregation", "") );
	for (i = 1 ; i < 32; i++) {
		var val = ((i < 10) ? "0" : "") + i
		dayOfMonth.append( new Option(val,val) );
	};

	$('#btn_go').click(function(){ updateData(selectedInterval(), selectedMetric(), oTable); });
})();