window.onload=function(){

new Vue({
  el: '#app1',
  data: {
    message: 'Hello Vue.js!'
  }
})


var data0 ;

new Vue({
    el: '#app',
    
    data: {
        users:  [          
            {"id":1, "name":"Tom","title": "bose"},
            {"id":2, "name":"bose"},
            {"id":3, "name":"Jack"},
            {"id":4, "name":"Jill"},
            {"id":4, "name":"bill"},
            {"id":4, "name":"aill"},
            {"id":4, "name":"cill"},
            {"id":4, "name":"dill"},
            {"id":4, "name":"eill"},
            {"id":4, "name":"cill"},
            {"id":4, "name":"dill"},
            {"id":4, "name":"eill","title": "bose"},
            {"id":4, "name":"cill"},
            {"id":4, "name":"dill"},
            {"id":4, "name":"eill"},
            {"id":4, "name":"cill"},
            {"id":4, "name":"dill"},
            {"id":4, "name":"eill"},
            {"id":4, "name":"cill"},
            {"id":4, "name":"dill"},
            {"id":4, "name":"eill"},
            
        ],
        searchKey: '',
        currentPage: 0,
        itemsPerPage: 1,
        resultCount: 0
    },
    computed: {
        totalPages: function() {
          return Math.ceil(this.resultCount / this.itemsPerPage)
        }
    },
    methods: {
        setPage: function(pageNumber) {
          this.currentPage = pageNumber
        }
    },
    filters: {
        paginate: function(list) {
            this.resultCount = list.length
            if (this.currentPage >= this.totalPages) {
              this.currentPage = this.totalPages - 1
            }
            var index = this.currentPage * this.itemsPerPage
            return list.slice(index, index + this.itemsPerPage)
        }
    }
})
}
