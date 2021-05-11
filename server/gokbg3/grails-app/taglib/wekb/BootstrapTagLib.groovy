package wekb

class BootstrapTagLib {
    //static defaultEncodeAs = [taglib:'html']
    //static encodeAsForTags = [tagName: [taglib:'html'], otherTagName: [taglib:'none']]

    static namespace = 'bootStrap'


    def modal = { attrs, body ->

        String id           = attrs.id ? ' id="' + attrs.id + '" ' : ''
        String title        = attrs.title

        out << '<div' + id +' class="qmodal modal modal-wide" role="dialog" tabindex="-1">'
        out << '<div class="modal-dialog">'
        out << '<div class="modal-content">'

        out << '<div class="modal-header">'
        out << '<h3 class="modal-title">' + title +'</h3>'
        out << '<button type="button" class="close" data-dismiss="modal" aria-hidden="true">&times;</button>'
        out << '</div>'

        out << '<div class="modal-body">'
        out << body()
        out << '</div>'

        out << '<div class="modal-footer">'
        if (attrs.formID) {
            out << '<button type="submit" class="btn btn-default" onclick="event.preventDefault(); $(\'#' + attrs.id + '\').find(\'#' + attrs.formID + '\').submit()">Add</button>'
        } else {
            out << '<button type="submit" class="btn btn-default" onclick="event.preventDefault(); $(\'#' + attrs.id + '\').find(\'form\').submit()">Add</button>'
        }
        out << '</div>'
        out << '</div>'
        out << '</div>'
        out << '</div>'


    }
    def tab = { attrs, body ->

    }
}
